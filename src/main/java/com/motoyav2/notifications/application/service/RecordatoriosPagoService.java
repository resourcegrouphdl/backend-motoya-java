package com.motoyav2.notifications.application.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Servicio para generar recordatorios y alertas de cuotas vencidas/próximas a vencer.
 *
 * Diseñado para ser invocado desde:
 *   - Cloud Scheduler (7am Lima daily) → POST /api/v1/notificaciones/procesar-recordatorios
 *   - Manualmente desde el panel de administración
 *
 * Lógica:
 *   1. Consulta casos activos en 'cobranzas-casos'
 *   2. Filtra el cronograma embebido de cada caso en memoria
 *   3. Emite eventos CUOTA_POR_VENCER y CUOTA_VENCIDA vía NotificationFacade
 *
 * Anti-spam: CUOTA_VENCIDA solo se envía en días específicos post-vencimiento.
 * Anti-duplicados: cada ejecución del scheduler solo procesa el día actual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordatoriosPagoService {

    private final Firestore db;
    private final NotificationFacade notificationFacade;

    private static final String COL_CASOS = "cobranzas-casos";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Días post-vencimiento en que se envía alerta (evita spam diario)
    private static final Set<Long> DIAS_ALERTA_VENCIDA = Set.of(1L, 3L, 7L, 15L, 30L);

    public record RecordatoriosResult(int recordatoriosEnviados, int alertasVencidasEnviadas) {}

    /**
     * Genera recordatorios para el día actual.
     * Retorna el conteo de eventos emitidos.
     */
    public Mono<RecordatoriosResult> procesarRecordatorios() {
        LocalDate hoy = LocalDate.now();
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

    // ─── Privados ─────────────────────────────────────────────────────────────

    private Flux<CasoCobranzaDocument> fetchCasosActivos() {
        return Mono.fromCallable(() -> {
                    try {
                        // whereIn filtra solo los ciclos de vida que requieren seguimiento
                        // Evita whereNotEqualTo que requiere índice especial en Firestore
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

        Flux<Integer> recordatorios = Flux.fromIterable(caso.getCronograma())
                .filter(c -> {
                    if (c.getFechaVencimiento() == null) return false;
                    if ("PAGADA".equalsIgnoreCase(c.getEstado())) return false;
                    try {
                        LocalDate fechaVenc = LocalDate.parse(c.getFechaVencimiento());
                        return (fechaVenc.equals(en2Dias) || fechaVenc.equals(hoy.plusDays(1)))
                                && !fechaVenc.isBefore(hoy);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .flatMap(cuota -> {
                    LocalDate fechaVenc = LocalDate.parse(cuota.getFechaVencimiento());
                    String montoFmt = formatMonto(cuota.getMonto());
                    String fechaFmt = fechaVenc.format(DATE_FMT);

                    log.debug("[RECORDATORIOS] Cuota próxima | contratoId={} cuota={} fecha={}",
                            contratoId, cuota.getCuota(), fechaFmt);

                    return notificationFacade
                            .notificarRecordatorioCuota(contratoId, telefono, cliente, montoFmt, fechaFmt)
                            .onErrorResume(ex -> {
                                log.error("[RECORDATORIOS] Error recordatorio | contratoId={} error={}",
                                        contratoId, ex.getMessage());
                                return Mono.empty();
                            })
                            .thenReturn(1);
                });

        Flux<Integer> alertasVencidas = Flux.fromIterable(caso.getCronograma())
                .filter(c -> {
                    if (c.getFechaVencimiento() == null) return false;
                    if ("PAGADA".equalsIgnoreCase(c.getEstado())) return false;
                    try {
                        LocalDate fechaVenc = LocalDate.parse(c.getFechaVencimiento());
                        if (!fechaVenc.isBefore(hoy)) return false;
                        long diasVencido = ChronoUnit.DAYS.between(fechaVenc, hoy);
                        return DIAS_ALERTA_VENCIDA.contains(diasVencido);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .flatMap(cuota -> {
                    LocalDate fechaVenc = LocalDate.parse(cuota.getFechaVencimiento());
                    long diasVencido = ChronoUnit.DAYS.between(fechaVenc, hoy);
                    String montoFmt = formatMonto(cuota.getMonto());

                    log.debug("[RECORDATORIOS] Cuota vencida | contratoId={} cuota={} diasVencido={}",
                            contratoId, cuota.getCuota(), diasVencido);

                    return notificationFacade
                            .notificarCuotaVencida(contratoId, telefono, cliente,
                                    montoFmt, String.valueOf(diasVencido))
                            .onErrorResume(ex -> {
                                log.error("[RECORDATORIOS] Error alerta vencida | contratoId={} error={}",
                                        contratoId, ex.getMessage());
                                return Mono.empty();
                            })
                            .thenReturn(1);
                });

        Mono<Integer> totalRecordatorios = recordatorios.reduce(0, Integer::sum);
        Mono<Integer> totalAlertas = alertasVencidas.reduce(0, Integer::sum);

        return Mono.zip(totalRecordatorios, totalAlertas)
                .map(tuple -> new RecordatoriosResult(tuple.getT1(), tuple.getT2()));
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
