package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.PromesaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de procesamiento diario de mora.
 *
 * Responsabilidades:
 *  1. Marca cuotas como VENCIDA cuando su fecha de vencimiento ya pasó y no están pagadas.
 *  2. Calcula mora acumulada: días de mora × S/ 3.00 por día (regulación interna Motoya).
 *  3. Actualiza nivelEstrategia según tramos de días de mora.
 *  4. Envía recordatorio WhatsApp cada 3 días si el cliente no tiene promesa vigente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoraDiariaService {

    private static final ZoneId    LIMA                        = ZoneId.of("America/Lima");
    private static final double    MORA_DIARIA_SOLES           = 3.0;
    private static final int       FRECUENCIA_RECORDATORIO_DIAS = 3;

    private final CasoCobranzaPort   casoPort;
    private final EventoCobranzaPort eventoPort;
    private final PromesaPort        promesaPort;
    private final NotificationFacade notificationFacade;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Punto de entrada del scheduler. Procesa todos los casos activos en paralelo (concurrency=8).
     * Los errores por caso son silenciados para que un documento corrupto no detenga el lote.
     */
    public Flux<String> procesarMoraDiaria() {
        LocalDate hoy = LocalDate.now(LIMA);
        log.info("[MORA-DIARIA] Iniciando procesamiento para {}", hoy);

        return casoPort.findAll()
                .filter(c -> esCasoGestionable(c.getCicloVida()))
                .flatMap(caso -> procesarCaso(caso, hoy)
                        .onErrorResume(e -> {
                            log.warn("[MORA-DIARIA] Caso {} omitido por error: {}",
                                    caso.getContratoId(), e.getMessage());
                            return Mono.empty();
                        }), 8)
                .doOnComplete(() -> log.info("[MORA-DIARIA] Procesamiento completado para {}", hoy));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Mono<String> procesarCaso(CasoCobranzaDocument caso, LocalDate hoy) {
        List<CuotaCronogramaDocument> cronograma = caso.getCronograma();
        if (cronograma == null || cronograma.isEmpty()) return Mono.empty();

        // ── 1. Calcular mora y marcar cuotas VENCIDA ──────────────────────────
        boolean cronogramaModificado = false;
        int     diasMoraMax          = 0;
        double  montoCuotaMasVieja   = 0.0;

        for (CuotaCronogramaDocument cuota : cronograma) {
            if ("PAGADA".equals(cuota.getEstado())) continue;
            if (cuota.getFechaVencimiento() == null)  continue;

            try {
                LocalDate venc = LocalDate.parse(cuota.getFechaVencimiento());
                if (!venc.isBefore(hoy)) continue;                           // aún no vence

                long dias = ChronoUnit.DAYS.between(venc, hoy);
                if (dias > diasMoraMax) {
                    diasMoraMax        = (int) dias;
                    montoCuotaMasVieja = cuota.getMonto() != null ? cuota.getMonto() : 0.0;
                }
                if (!"VENCIDA".equals(cuota.getEstado())) {
                    cuota.setEstado("VENCIDA");
                    cronogramaModificado = true;
                }
            } catch (Exception e) {
                log.debug("[MORA-DIARIA] Fecha inválida cuota #{} contrato {}",
                        cuota.getCuota(), caso.getContratoId());
            }
        }

        if (diasMoraMax == 0) return Mono.empty();   // caso sin mora

        // ── 2. Valores derivados ──────────────────────────────────────────────
        final int    diasMora   = diasMoraMax;
        final double moraDiaria = diasMora * MORA_DIARIA_SOLES;
        final double montoTotal = montoCuotaMasVieja + moraDiaria;
        final double montoCuota = montoCuotaMasVieja;

        final String nivelAnterior = caso.getNivelEstrategia();
        final String nuevoNivel    = calcularNivel(diasMora);
        final boolean nivelCambio  = !nuevoNivel.equals(nivelAnterior);
        final boolean cronoChanged = cronogramaModificado;

        // ── 3. Mutar el caso (sin save aún) ──────────────────────────────────
        caso.setTotalMora(moraDiaria);
        caso.setNivelEstrategia(nuevoNivel);
        caso.setActualizadoEn(new Date());
        if (cronoChanged) caso.setCronograma(cronograma);

        // ── 4. Verificar si tiene promesa vigente para decidir WA ─────────────
        return promesaPort.findVigente(caso.getContratoId())
                .hasElement()
                .flatMap(tienePromesa -> {
                    boolean debeNotificar = !tienePromesa
                            && debeEnviarRecordatorio(caso.getUltimoRecordatorioMora());

                    if (debeNotificar) {
                        caso.setUltimoRecordatorioMora(new Date());
                    }

                    return casoPort.save(caso)
                            .flatMap(saved -> {
                                Mono<Void> evNivel = nivelCambio
                                        ? appendEventoNivelEscalado(
                                                caso.getContratoId(), nivelAnterior,
                                                nuevoNivel, diasMora, moraDiaria)
                                        : Mono.empty();

                                Mono<Void> notifWa = debeNotificar
                                        ? enviarRecordatorioWa(
                                                caso, diasMora, montoCuota, moraDiaria, montoTotal)
                                        : Mono.empty();

                                return Mono.when(evNivel, notifWa)
                                        .thenReturn(caso.getContratoId());
                            });
                });
    }

    // ── WA recordatorio de mora ───────────────────────────────────────────────

    private Mono<Void> enviarRecordatorioWa(CasoCobranzaDocument caso,
                                             int diasMora, double montoCuota,
                                             double montoMora, double montoTotal) {
        String telefono = caso.getClienteTelefono();
        String cliente  = caso.getClienteNombre();
        if (telefono == null || telefono.isBlank()) return Mono.empty();

        Mono<Void> wa = notificationFacade.notificarCuotaVencida(
                caso.getContratoId(), telefono, cliente,
                fmt(montoCuota),
                String.valueOf(diasMora),
                fmt(montoMora),
                fmt(montoTotal))
                .onErrorResume(e -> {
                    log.warn("[MORA-DIARIA] Error WA {}: {}", caso.getContratoId(), e.getMessage());
                    return Mono.empty();
                });

        EventoCobranzaDocument ev = EventoCobranzaDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(caso.getContratoId())
                .tipo("MENSAJE_WHATSAPP")
                .payload(Map.of(
                        "tipo",       "RECORDATORIO_MORA",
                        "diasMora",   diasMora,
                        "montoMora",  montoMora,
                        "montoTotal", montoTotal
                ))
                .usuarioId("SISTEMA")
                .usuarioNombre("Sistema Automático")
                .automatico(true)
                .creadoEn(new Date())
                .build();

        return wa.then(eventoPort.append(caso.getContratoId(), ev));
    }

    // ── Evento de escalado de nivel ───────────────────────────────────────────

    private Mono<Void> appendEventoNivelEscalado(String contratoId,
                                                   String nivelAnterior, String nivelNuevo,
                                                   int diasMora, double moraDiaria) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nivelAnterior",   nivelAnterior != null ? nivelAnterior : "");
        payload.put("nivelNuevo",      nivelNuevo);
        payload.put("diasMora",        diasMora);
        payload.put("moraDiaria",      moraDiaria);
        payload.put("moraDiariaSoles", MORA_DIARIA_SOLES);

        EventoCobranzaDocument ev = EventoCobranzaDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(contratoId)
                .tipo("NIVEL_ESCALADO")
                .payload(payload)
                .usuarioId("SISTEMA")
                .usuarioNombre("Sistema Automático")
                .automatico(true)
                .creadoEn(new Date())
                .build();

        return eventoPort.append(contratoId, ev);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean esCasoGestionable(String cicloVida) {
        if (cicloVida == null) return true;
        return switch (cicloVida) {
            case "PAGADO_TOTAL", "CASTIGADO", "CERRADO", "CANCELADO" -> false;
            default -> true;
        };
    }

    private boolean debeEnviarRecordatorio(Date ultimoRecordatorio) {
        if (ultimoRecordatorio == null) return true;
        LocalDate ultima = ultimoRecordatorio.toInstant().atZone(LIMA).toLocalDate();
        return ChronoUnit.DAYS.between(ultima, LocalDate.now(LIMA)) >= FRECUENCIA_RECORDATORIO_DIAS;
    }

    /** Tramos regulación interna Motoya: 1-15d TEMPRANA · 16-30d MEDIA · 31-60d CRITICA · 61+d JUDICIAL */
    private String calcularNivel(int diasMora) {
        if (diasMora >= 61) return "JUDICIAL";
        if (diasMora >= 31) return "MORA_CRITICA";
        if (diasMora >= 16) return "MORA_MEDIA";
        return "MORA_TEMPRANA";
    }

    private String fmt(double monto) {
        return String.format("S/ %.2f", monto);
    }
}
