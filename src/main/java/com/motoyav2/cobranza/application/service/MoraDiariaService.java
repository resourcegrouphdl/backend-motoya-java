package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.AlertaCobranzaPort;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.domain.NivelMoraCalculadora;
import com.motoyav2.cobranza.domain.enums.NivelAlerta;
import com.motoyav2.cobranza.domain.enums.NivelEstrategia;
import com.motoyav2.cobranza.domain.enums.TipoAlerta;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AlertaCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cálculo diario de mora — responsabilidad única: números y estado.
 *
 * En cada ejecución (07:15 Lima vía CobranzaSchedulerOrchestrator):
 *   1. Marca cuotas como VENCIDA cuando su fecha ya pasó.
 *   2. Calcula mora acumulada (S/ 3.00/día).
 *   3. Escala nivelEstrategia según tramos Motoya via NivelMoraCalculadora.
 *   4. Si el nivel escala a MORA_CRITICA o JUDICIAL → crea alerta en cobranzas-alertas.
 *
 * NO envía WhatsApp — esa responsabilidad es exclusiva de CobranzaRecordatorioService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoraDiariaService {

    private final CasoCobranzaPort   casoPort;
    private final EventoCobranzaPort eventoPort;
    private final AlertaCobranzaPort alertaPort;

    // ─────────────────────────────────────────────────────────────────────────

    public Flux<String> procesarMora() {
        LocalDate hoy = LocalDate.now(NivelMoraCalculadora.LIMA);
        log.info("[MORA] Iniciando cálculo diario para {}", hoy);

        return casoPort.findAll()
                .filter(c -> esCasoGestionable(c.getCicloVida()))
                .flatMap(caso -> procesarCaso(caso, hoy)
                        .onErrorResume(e -> {
                            log.warn("[MORA] Caso {} omitido: {}", caso.getContratoId(), e.getMessage());
                            return Mono.empty();
                        }), 8)
                .doOnComplete(() -> log.info("[MORA] Cálculo completado para {}", hoy));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Mono<String> procesarCaso(CasoCobranzaDocument caso, LocalDate hoy) {
        List<CuotaCronogramaDocument> cronograma = caso.getCronograma();
        if (cronograma == null || cronograma.isEmpty()) return Mono.empty();

        // ── 1. Marcar cuotas VENCIDA ─────────────────────────────────────────
        boolean cronogramaModificado = false;
        for (CuotaCronogramaDocument cuota : cronograma) {
            if ("PAGADA".equals(cuota.getEstado()) || cuota.getFechaVencimiento() == null) continue;
            try {
                LocalDate venc = LocalDate.parse(cuota.getFechaVencimiento());
                if (venc.isBefore(hoy) && !"VENCIDA".equals(cuota.getEstado())) {
                    cuota.setEstado("VENCIDA");
                    cronogramaModificado = true;
                }
            } catch (Exception e) {
                log.debug("[MORA] Fecha inválida cuota #{} contrato {}",
                        cuota.getCuota(), caso.getContratoId());
            }
        }

        int diasMora = NivelMoraCalculadora.diasMora(caso, hoy);
        if (diasMora == 0) return Mono.empty();

        // ── 2. Calcular nivel y mora ─────────────────────────────────────────
        double moraSoles     = NivelMoraCalculadora.moraSoles(diasMora);
        String nivelAnterior = caso.getNivelEstrategia();
        String nuevoNivel    = NivelMoraCalculadora.calcularNivel(diasMora);
        boolean nivelCambio  = nuevoNivel != null && !nuevoNivel.equals(nivelAnterior);
        boolean esEscaladaCritica = nivelCambio
                && (NivelEstrategia.MORA_CRITICA.name().equals(nuevoNivel)
                    || NivelEstrategia.JUDICIAL.name().equals(nuevoNivel));

        // ── 3. Actualizar documento ──────────────────────────────────────────
        caso.setTotalMora(moraSoles);
        if (nuevoNivel != null) caso.setNivelEstrategia(nuevoNivel);
        caso.setActualizadoEn(new Date());
        if (cronogramaModificado) caso.setCronograma(cronograma);

        // ── 4. Persistir y disparar efectos secundarios ──────────────────────
        return casoPort.save(caso)
                .flatMap(saved -> {
                    Mono<Void> evNivel = nivelCambio
                            ? appendEventoNivel(caso.getContratoId(), nivelAnterior,
                                                nuevoNivel, diasMora, moraSoles)
                            : Mono.empty();

                    Mono<Void> alerta = esEscaladaCritica
                            ? crearAlertaEscalada(caso, nuevoNivel, diasMora, moraSoles)
                            : Mono.empty();

                    return Mono.when(evNivel, alerta).thenReturn(caso.getContratoId());
                });
    }

    // ── Evento de escalado ────────────────────────────────────────────────────

    private Mono<Void> appendEventoNivel(String contratoId, String anterior, String nuevo,
                                          int diasMora, double moraSoles) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nivelAnterior",   anterior != null ? anterior : "");
        payload.put("nivelNuevo",      nuevo);
        payload.put("diasMora",        diasMora);
        payload.put("moraSoles",       moraSoles);
        payload.put("moraDiariaSoles", NivelMoraCalculadora.MORA_DIARIA_SOLES);

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

        return eventoPort.append(contratoId, ev).then();
    }

    // ── Alerta operativa cuando escala a CRITICA o JUDICIAL ──────────────────

    private Mono<Void> crearAlertaEscalada(CasoCobranzaDocument caso,
                                            String nuevoNivel, int diasMora, double moraSoles) {
        boolean esJudicial = NivelEstrategia.JUDICIAL.name().equals(nuevoNivel);

        AlertaCobranzaDocument alerta = AlertaCobranzaDocument.builder()
                .id(UUID.randomUUID().toString())
                .tipo(TipoAlerta.ESCALACION_REQUERIDA.name())
                .nivel(esJudicial ? NivelAlerta.CRITICAL.name() : NivelAlerta.WARNING.name())
                .titulo((esJudicial ? "🚨 Caso judicial: " : "⚠️ Mora crítica: ")
                        + caso.getClienteNombre())
                .descripcion(String.format("Escalado a %s — %d días de mora (%s)",
                        nuevoNivel, diasMora, NivelMoraCalculadora.fmt(moraSoles)))
                .contratoId(caso.getContratoId())
                .clienteNombre(caso.getClienteNombre())
                .storeId(caso.getStoreId())
                .agenteId(caso.getAgenteAsignadoId())
                .accionSugerida("Gestionar caso con urgencia")
                .accionRuta("/cobranzas/vista360/" + caso.getContratoId())
                .leida(false)
                .descartada(false)
                .creadoEn(new Date())
                .expiraEn(new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
                .build();

        return alertaPort.save(alerta)
                .doOnSuccess(a -> log.info("[MORA] Alerta operativa creada | contrato={} nivel={}",
                        caso.getContratoId(), nuevoNivel))
                .onErrorResume(e -> {
                    log.warn("[MORA] Error creando alerta | contrato={}: {}",
                            caso.getContratoId(), e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean esCasoGestionable(String cicloVida) {
        if (cicloVida == null) return true;
        return switch (cicloVida) {
            case "PAGADO_TOTAL", "CASTIGADO", "CERRADO", "CANCELADO" -> false;
            default -> true;
        };
    }
}