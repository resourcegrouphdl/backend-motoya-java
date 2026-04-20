package com.motoyav2.notifications.application.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.PromesaPort;
import com.motoyav2.cobranza.application.port.out.VoucherPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PromesaDocument;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Genera recordatorios y alertas de cuotas vencidas/próximas a vencer.
 *
 * Lógica de ejecución diaria (7am Lima):
 *   1. Carga casos activos de Firestore
 *   2. Auto-escala nivelEstrategia según días en mora
 *   3. Si existe promesa VIGENTE:
 *      - Fecha pasada → marca INCUMPLIDA, procesa alertas vencidas
 *      - Fecha = hoy  → envía recordatorio de promesa, no duplica alerta vencida
 *      - Fecha futura → pausa alertas vencidas (promesa activa, no spamear)
 *   4. Si hay voucher PENDIENTE → pausa alertas vencidas (revisión en curso)
 *   5. Sin obstáculos → envía recordatorios previos + alertas vencidas (anti-spam por días)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordatoriosPagoService {

    private final Firestore             db;
    private final NotificationFacade   notificationFacade;
    private final PromesaPort          promesaPort;
    private final VoucherPort          voucherPort;
    private final CasoCobranzaPort     casoPort;

    private static final String        COL_CASOS       = "cobranzas-casos";
    private static final DateTimeFormatter DATE_FMT    = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<Long>     DIAS_ALERTA_VENCIDA = Set.of(1L, 3L, 7L, 15L, 30L);

    public record RecordatoriosResult(int recordatoriosEnviados, int alertasVencidasEnviadas) {}

    public Mono<RecordatoriosResult> procesarRecordatorios() {
        LocalDate hoy    = LocalDate.now();
        LocalDate en2Dias = hoy.plusDays(2);

        log.info("[RECORDATORIOS] Procesando recordatorios para fecha={}", hoy);

        return fetchCasosActivos()
                .flatMap(caso -> procesarCaso(caso, hoy, en2Dias))
                .reduce(new RecordatoriosResult(0, 0),
                        (acc, delta) -> new RecordatoriosResult(
                                acc.recordatoriosEnviados() + delta.recordatoriosEnviados(),
                                acc.alertasVencidasEnviadas() + delta.alertasVencidasEnviadas()))
                .doOnSuccess(r -> log.info(
                        "[RECORDATORIOS] ✓ Completado | recordatorios={} alertasVencidas={}",
                        r.recordatoriosEnviados(), r.alertasVencidasEnviadas()));
    }

    // ─── Orquestación por caso ────────────────────────────────────────────────

    private Mono<RecordatoriosResult> procesarCaso(CasoCobranzaDocument caso,
                                                    LocalDate hoy, LocalDate en2Dias) {
        if (caso.getCronograma() == null || caso.getCronograma().isEmpty()) {
            return Mono.just(new RecordatoriosResult(0, 0));
        }

        String contratoId = caso.getContratoId();
        String telefono   = caso.getClienteTelefono();
        String cliente    = caso.getClienteNombre();

        if (telefono == null || telefono.isBlank()) {
            log.warn("[RECORDATORIOS] Caso sin teléfono | contratoId={}", contratoId);
            return Mono.just(new RecordatoriosResult(0, 0));
        }

        return actualizarNivelEstrategia(caso, hoy)
                .then(
                    promesaPort.findVigente(contratoId)
                            .flatMap(promesa -> manejarPromesaVigente(caso, promesa, hoy, en2Dias, telefono, cliente))
                            .switchIfEmpty(
                                tieneVoucherPendiente(contratoId)
                                        .flatMap(pendiente -> {
                                            if (Boolean.TRUE.equals(pendiente)) {
                                                log.debug("[RECORDATORIOS] Voucher pendiente → pausa alertas | contratoId={}", contratoId);
                                                return enviarSoloRecordatoriosPrevios(caso, hoy, en2Dias, contratoId, telefono, cliente);
                                            }
                                            return enviarAlertas(caso, hoy, en2Dias, contratoId, telefono, cliente);
                                        })
                            )
                );
    }

    // ─── Lógica de promesas ───────────────────────────────────────────────────

    private Mono<RecordatoriosResult> manejarPromesaVigente(
            CasoCobranzaDocument caso, PromesaDocument promesa,
            LocalDate hoy, LocalDate en2Dias, String telefono, String cliente) {

        String contratoId = caso.getContratoId();
        LocalDate fechaPromesa;
        try {
            fechaPromesa = LocalDate.parse(promesa.getFecha());
        } catch (Exception e) {
            log.warn("[RECORDATORIOS] Fecha promesa inválida | contratoId={} fecha={}", contratoId, promesa.getFecha());
            return enviarAlertas(caso, hoy, en2Dias, contratoId, telefono, cliente);
        }

        if (fechaPromesa.isBefore(hoy)) {
            log.info("[RECORDATORIOS] Promesa incumplida | contratoId={} fechaPromesa={}", contratoId, promesa.getFecha());
            return marcarPromesaIncumplida(caso, promesa)
                    .then(enviarAlertas(caso, hoy, en2Dias, contratoId, telefono, cliente));
        }

        if (fechaPromesa.equals(hoy)) {
            log.info("[RECORDATORIOS] Promesa vence hoy | contratoId={} monto={}", contratoId, promesa.getMonto());
            return marcarEstadoCasoPromesaHoy(caso)
                    .then(notificationFacade.notificarRecordatorioPromesa(
                            contratoId, telefono, cliente, formatMonto(promesa.getMonto())))
                    .onErrorResume(e -> Mono.empty())
                    .thenReturn(new RecordatoriosResult(1, 0));
        }

        // Promesa futura activa → pausar alertas vencidas, enviar solo pre-vencimiento
        log.debug("[RECORDATORIOS] Promesa activa futura → pausa alertas | contratoId={} fechaPromesa={}", contratoId, promesa.getFecha());
        return enviarSoloRecordatoriosPrevios(caso, hoy, en2Dias, contratoId, telefono, cliente);
    }

    private Mono<Void> marcarPromesaIncumplida(CasoCobranzaDocument caso, PromesaDocument promesa) {
        promesa.setEstado("INCUMPLIDA");
        promesa.setCerradaEn(new Date());
        promesa.setMotivoCierre("Fecha de compromiso vencida sin pago detectado");
        promesa.setActualizadoEn(new Date());

        caso.setEstadoCaso("PROMESA_INCUMPLIDA");
        caso.setActualizadoEn(new Date());

        return promesaPort.save(promesa.getContratoId(), promesa)
                .then(casoPort.save(caso))
                .then();
    }

    private Mono<Void> marcarEstadoCasoPromesaHoy(CasoCobranzaDocument caso) {
        caso.setEstadoCaso("PROMESA_VENCE_HOY");
        caso.setActualizadoEn(new Date());
        return casoPort.save(caso).then();
    }

    // ─── Escalación automática de nivel ──────────────────────────────────────

    /**
     * Calcula el nivel de estrategia según los días de la cuota más vencida
     * y actualiza el caso en Firestore solo si el nivel cambió.
     *
     * Umbrales: 1-7 → MORA_TEMPRANA, 8-30 → MORA_MEDIA, 31-60 → MORA_CRITICA, 61+ → JUDICIAL
     */
    private Mono<Void> actualizarNivelEstrategia(CasoCobranzaDocument caso, LocalDate hoy) {
        if (caso.getCronograma() == null) return Mono.empty();

        long maxDiasVencido = caso.getCronograma().stream()
                .filter(c -> c.getFechaVencimiento() != null
                        && !"PAGADA".equalsIgnoreCase(c.getEstado()))
                .mapToLong(c -> {
                    try {
                        LocalDate fv = LocalDate.parse(c.getFechaVencimiento());
                        return fv.isBefore(hoy) ? ChronoUnit.DAYS.between(fv, hoy) : 0;
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);

        if (maxDiasVencido <= 0) return Mono.empty();

        String nivelNuevo  = calcularNivel(maxDiasVencido);
        String nivelActual = caso.getNivelEstrategia();
        if (nivelNuevo.equals(nivelActual)) return Mono.empty();

        log.info("[RECORDATORIOS] Escalando nivel | contratoId={} {} → {} diasMaxMora={}",
                caso.getContratoId(), nivelActual, nivelNuevo, maxDiasVencido);

        caso.setNivelEstrategia(nivelNuevo);
        caso.setActualizadoEn(new Date());
        return casoPort.save(caso).then();
    }

    private String calcularNivel(long diasVencido) {
        if (diasVencido >= 61) return "JUDICIAL";
        if (diasVencido >= 31) return "MORA_CRITICA";
        if (diasVencido >= 8)  return "MORA_MEDIA";
        return "MORA_TEMPRANA";
    }

    // ─── Envío de alertas ─────────────────────────────────────────────────────

    /** Solo recordatorios pre-vencimiento (sin alertas de mora). */
    private Mono<RecordatoriosResult> enviarSoloRecordatoriosPrevios(
            CasoCobranzaDocument caso, LocalDate hoy, LocalDate en2Dias,
            String contratoId, String telefono, String cliente) {

        Mono<Integer> total = Flux.fromIterable(caso.getCronograma())
                .filter(c -> filtroPrevencimiento(c, hoy, en2Dias))
                .flatMap(cuota -> {
                    LocalDate fechaVenc = LocalDate.parse(cuota.getFechaVencimiento());
                    return notificationFacade.notificarRecordatorioCuota(
                                    contratoId, telefono, cliente,
                                    formatMonto(cuota.getMonto()),
                                    fechaVenc.format(DATE_FMT))
                            .onErrorResume(e -> {
                                log.error("[RECORDATORIOS] Error recordatorio | contratoId={} error={}",
                                        contratoId, e.getMessage());
                                return Mono.empty();
                            })
                            .thenReturn(1);
                })
                .reduce(0, Integer::sum);

        return total.map(r -> new RecordatoriosResult(r, 0));
    }

    /** Recordatorios pre-vencimiento + alertas vencidas (flujo normal). */
    private Mono<RecordatoriosResult> enviarAlertas(
            CasoCobranzaDocument caso, LocalDate hoy, LocalDate en2Dias,
            String contratoId, String telefono, String cliente) {

        Flux<Integer> recordatorios = Flux.fromIterable(caso.getCronograma())
                .filter(c -> filtroPrevencimiento(c, hoy, en2Dias))
                .flatMap(cuota -> {
                    LocalDate fechaVenc = LocalDate.parse(cuota.getFechaVencimiento());
                    return notificationFacade.notificarRecordatorioCuota(
                                    contratoId, telefono, cliente,
                                    formatMonto(cuota.getMonto()),
                                    fechaVenc.format(DATE_FMT))
                            .onErrorResume(ex -> {
                                log.error("[RECORDATORIOS] Error recordatorio | contratoId={} error={}",
                                        contratoId, ex.getMessage());
                                return Mono.empty();
                            })
                            .thenReturn(1);
                });

        Flux<Integer> alertasVencidas = Flux.fromIterable(caso.getCronograma())
                .filter(c -> filtroVencida(c, hoy))
                .flatMap(cuota -> {
                    LocalDate fechaVenc   = LocalDate.parse(cuota.getFechaVencimiento());
                    long diasVencido      = ChronoUnit.DAYS.between(fechaVenc, hoy);
                    double montoBase      = cuota.getMonto() != null ? cuota.getMonto() : 0.0;
                    double montoMora      = diasVencido * 3.0;
                    double montoTotal     = montoBase + montoMora;

                    log.debug("[RECORDATORIOS] Cuota vencida | contratoId={} cuota={} diasVencido={} mora={}",
                            contratoId, cuota.getCuota(), diasVencido, montoMora);

                    return notificationFacade.notificarCuotaVencida(
                                    contratoId, telefono, cliente,
                                    formatMonto(montoBase),
                                    String.valueOf(diasVencido),
                                    formatMonto(montoMora),
                                    formatMonto(montoTotal))
                            .onErrorResume(ex -> {
                                log.error("[RECORDATORIOS] Error alerta vencida | contratoId={} error={}",
                                        contratoId, ex.getMessage());
                                return Mono.empty();
                            })
                            .thenReturn(1);
                });

        return Mono.zip(recordatorios.reduce(0, Integer::sum),
                        alertasVencidas.reduce(0, Integer::sum))
                .map(t -> new RecordatoriosResult(t.getT1(), t.getT2()));
    }

    // ─── Filtros de cronograma ────────────────────────────────────────────────

    private boolean filtroPrevencimiento(
            com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument c,
            LocalDate hoy, LocalDate en2Dias) {
        if (c.getFechaVencimiento() == null) return false;
        if ("PAGADA".equalsIgnoreCase(c.getEstado())) return false;
        try {
            LocalDate fv = LocalDate.parse(c.getFechaVencimiento());
            return (fv.equals(en2Dias) || fv.equals(hoy.plusDays(1))) && !fv.isBefore(hoy);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean filtroVencida(
            com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument c,
            LocalDate hoy) {
        if (c.getFechaVencimiento() == null) return false;
        if ("PAGADA".equalsIgnoreCase(c.getEstado())) return false;
        try {
            LocalDate fv = LocalDate.parse(c.getFechaVencimiento());
            if (!fv.isBefore(hoy)) return false;
            long diasVencido = ChronoUnit.DAYS.between(fv, hoy);
            return DIAS_ALERTA_VENCIDA.contains(diasVencido);
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Mono<Boolean> tieneVoucherPendiente(String contratoId) {
        return voucherPort.findByContratoId(contratoId)
                .filter(v -> "PENDIENTE".equals(v.getEstado()))
                .hasElements();
    }

    private Flux<CasoCobranzaDocument> fetchCasosActivos() {
        return Mono.fromCallable(() -> {
                    try {
                        return db.collection(COL_CASOS)
                                .whereIn("cicloVida", List.of(
                                        "ACTIVO", "PROMESA_VIGENTE", "ACUERDO_VIGENTE"))
                                .get()
                                .get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException("Error consultando casos activos", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(snapshot ->
                        Flux.fromIterable(snapshot.getDocuments())
                                .mapNotNull(doc -> safeDeserialize(doc))
                )
                .filter(caso -> esActivo(caso));
    }

    private CasoCobranzaDocument safeDeserialize(QueryDocumentSnapshot doc) {
        try {
            return doc.toObject(CasoCobranzaDocument.class);
        } catch (Exception e) {
            log.warn("[RECORDATORIOS] Documento omitido por error de deserialización | id={} error={}",
                    doc.getId(), e.getMessage());
            return null;
        }
    }

    private boolean esActivo(CasoCobranzaDocument caso) {
        if (caso == null) return false;
        String ciclo = caso.getCicloVida();
        if (ciclo == null) return false;
        return !List.of("PAGADO_TOTAL", "JUDICIAL", "CASTIGADO", "CERRADO").contains(ciclo);
    }

    private String formatMonto(Double monto) {
        if (monto == null) return "S/ 0.00";
        return String.format("S/ %.2f", monto);
    }
}
