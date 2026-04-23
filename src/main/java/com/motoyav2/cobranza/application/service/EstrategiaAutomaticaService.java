package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.EstrategiaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.PromesaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EstrategiaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Ejecuta las estrategias activas contra la cartera de casos.
 *
 * Para cada estrategia WHATSAPP activa:
 *  - Selecciona casos cuyo diasMora esté en el rango [diasMoraDesde, diasMoraHasta].
 *  - Respeta la frecuencia de envío (frecuenciaDias) usando ultimoRecordatorioMora.
 *  - Solo opera dentro del horario configurado por estrategia (horarioDesde-horarioHasta).
 *  - No contacta casos con promesa VIGENTE (ya están gestionados).
 *  - Registra evento ESTRATEGIA_DISPARADA por cada caso contactado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EstrategiaAutomaticaService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final EstrategiaPort     estrategiaPort;
    private final CasoCobranzaPort   casoPort;
    private final PromesaPort        promesaPort;
    private final EventoCobranzaPort eventoPort;
    private final NotificationFacade notificationFacade;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Evalúa y dispara todas las estrategias WHATSAPP activas.
     * Devuelve el número total de casos contactados.
     */
    public Mono<Integer> ejecutarEstrategiasActivas() {
        LocalDate hoy        = LocalDate.now(LIMA);
        LocalTime horaActual = LocalTime.now(LIMA);

        log.info("[ESTRATEGIA-AUTO] Evaluando estrategias activas — hora Lima: {}", horaActual);

        return estrategiaPort.findAll()
                .filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .filter(e -> "WHATSAPP".equals(e.getCanal()))
                .filter(e -> estaEnHorario(e, horaActual))
                .flatMap(estrategia -> ejecutarEstrategia(estrategia, hoy), 2) // 2 estrategias paralelas
                .reduce(0, Integer::sum)
                .doOnNext(total -> log.info("[ESTRATEGIA-AUTO] Ciclo completado — casos contactados: {}", total));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Mono<Integer> ejecutarEstrategia(EstrategiaDocument estrategia, LocalDate hoy) {
        log.debug("[ESTRATEGIA-AUTO] Evaluando: {} (nivel={} diasMora={}-{})",
                estrategia.getNombre(), estrategia.getNivel(),
                estrategia.getDiasMoraDesde(), estrategia.getDiasMoraHasta());

        return casoPort.findAll()
                .filter(caso -> esCasoGestionable(caso.getCicloVida()))
                .filter(caso -> coincideNivel(caso, estrategia))
                .filter(caso -> diasMoraEnRango(caso, estrategia, hoy))
                .flatMap(caso -> debeContactar(caso, estrategia)
                        .filter(Boolean.TRUE::equals)
                        .flatMap(__ -> contactarCaso(caso, estrategia, hoy))
                        .onErrorResume(e -> {
                            log.warn("[ESTRATEGIA-AUTO] Error en caso {}: {}",
                                    caso.getContratoId(), e.getMessage());
                            return Mono.empty();
                        }), 4)
                .count()
                .map(Long::intValue);
    }

    private Mono<Void> contactarCaso(CasoCobranzaDocument caso,
                                      EstrategiaDocument estrategia,
                                      LocalDate hoy) {
        String telefono = caso.getClienteTelefono();
        String cliente  = caso.getClienteNombre();
        if (telefono == null || telefono.isBlank()) return Mono.empty();

        int diasMora   = calcularDiasMora(caso, hoy);
        double mora    = diasMora * 3.0;
        double cuota   = primeraCuotaVencida(caso);
        double total   = cuota + mora;

        // Marcar fecha de último recordatorio
        caso.setUltimoRecordatorioMora(new Date());
        caso.setActualizadoEn(new Date());

        // Evento de auditoría
        EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(caso.getContratoId())
                .tipo("ESTRATEGIA_DISPARADA")
                .payload(Map.of(
                        "estrategiaId",     estrategia.getId(),
                        "estrategiaNombre", estrategia.getNombre(),
                        "nivel",            estrategia.getNivel(),
                        "diasMora",         diasMora,
                        "montoMora",        mora,
                        "manual",           false
                ))
                .usuarioId("SISTEMA")
                .usuarioNombre("Sistema Automático")
                .automatico(true)
                .creadoEn(new Date())
                .build();

        Mono<Void> wa = notificationFacade.notificarCuotaVencida(
                        caso.getContratoId(), telefono, cliente,
                        fmt(cuota), String.valueOf(diasMora), fmt(mora), fmt(total))
                .onErrorResume(e -> {
                    log.warn("[ESTRATEGIA-AUTO] WA fallido {}: {}", caso.getContratoId(), e.getMessage());
                    return Mono.empty();
                });

        return casoPort.save(caso)
                .then(eventoPort.append(caso.getContratoId(), evento))
                .then(wa);
    }

    // ── Predicados ───────────────────────────────────────────────────────────

    private Mono<Boolean> debeContactar(CasoCobranzaDocument caso, EstrategiaDocument estrategia) {
        // No contactar si tiene promesa VIGENTE
        return promesaPort.findVigente(caso.getContratoId())
                .hasElement()
                .map(tienePromesa -> {
                    if (tienePromesa) return false;
                    // Respetar frecuencia de la estrategia
                    Date ultimo = caso.getUltimoRecordatorioMora();
                    if (ultimo == null) return true;
                    LocalDate ultimaFecha = ultimo.toInstant().atZone(LIMA).toLocalDate();
                    int diasTranscurridos = (int) ChronoUnit.DAYS.between(ultimaFecha, LocalDate.now(LIMA));
                    int frecuencia = estrategia.getFrecuenciaDias() != null
                            ? estrategia.getFrecuenciaDias() : 3;
                    return diasTranscurridos >= frecuencia;
                });
    }

    private boolean coincideNivel(CasoCobranzaDocument caso, EstrategiaDocument estrategia) {
        // Si la estrategia especifica un nivel, filtrar por él
        if (estrategia.getNivel() == null) return true;
        return estrategia.getNivel().equals(caso.getNivelEstrategia());
    }

    private boolean diasMoraEnRango(CasoCobranzaDocument caso,
                                     EstrategiaDocument estrategia,
                                     LocalDate hoy) {
        int diasMora = calcularDiasMora(caso, hoy);
        if (diasMora <= 0) return false;
        if (estrategia.getDiasMoraDesde() != null && diasMora < estrategia.getDiasMoraDesde()) return false;
        if (estrategia.getDiasMoraHasta() != null && diasMora > estrategia.getDiasMoraHasta()) return false;
        return true;
    }

    private boolean estaEnHorario(EstrategiaDocument estrategia, LocalTime ahora) {
        try {
            if (estrategia.getHorarioDesde() != null) {
                LocalTime desde = LocalTime.parse(estrategia.getHorarioDesde());
                if (ahora.isBefore(desde)) return false;
            }
            if (estrategia.getHorarioHasta() != null) {
                LocalTime hasta = LocalTime.parse(estrategia.getHorarioHasta());
                if (ahora.isAfter(hasta)) return false;
            }
        } catch (Exception e) {
            log.debug("[ESTRATEGIA-AUTO] Horario inválido en estrategia {}", estrategia.getId());
        }
        return true;
    }

    private boolean esCasoGestionable(String cicloVida) {
        if (cicloVida == null) return true;
        return switch (cicloVida) {
            case "PAGADO_TOTAL", "CASTIGADO", "CERRADO", "CANCELADO" -> false;
            default -> true;
        };
    }

    // ── Helpers de mora ──────────────────────────────────────────────────────

    private int calcularDiasMora(CasoCobranzaDocument caso, LocalDate hoy) {
        if (caso.getFechaVencimientoPrimerCuotaImpaga() == null) return 0;
        try {
            LocalDate venc = caso.getFechaVencimientoPrimerCuotaImpaga()
                    .toInstant().atZone(LIMA).toLocalDate();
            return (int) Math.max(0, ChronoUnit.DAYS.between(venc, hoy));
        } catch (Exception e) {
            return 0;
        }
    }

    /** Monto de la primera cuota vencida del cronograma (para el mensaje WA). */
    private double primeraCuotaVencida(CasoCobranzaDocument caso) {
        if (caso.getCronograma() == null) return 0.0;
        LocalDate hoy = LocalDate.now(LIMA);

        // 1. Buscar cuota explícitamente marcada VENCIDA
        java.util.OptionalDouble vencida = caso.getCronograma().stream()
                .filter(c -> "VENCIDA".equals(c.getEstado()))
                .mapToDouble(c -> c.getMonto() != null ? c.getMonto() : 0.0)
                .findFirst();
        if (vencida.isPresent()) return vencida.getAsDouble();

        // 2. Fallback: primera cuota no pagada con fecha ya vencida (aún sin marcar)
        return caso.getCronograma().stream()
                .filter(c -> !"PAGADA".equals(c.getEstado()) && c.getFechaVencimiento() != null)
                .filter(c -> {
                    try { return LocalDate.parse(c.getFechaVencimiento()).isBefore(hoy); }
                    catch (Exception e) { return false; }
                })
                .mapToDouble(c -> c.getMonto() != null ? c.getMonto() : 0.0)
                .findFirst()
                .orElseGet(() -> {
                    log.warn("[ESTRATEGIA-AUTO] Sin cuota vencida en cronograma para contrato={}; revisar datos",
                            caso.getContratoId());
                    return 0.0;   // nunca usar saldoActual (deuda total) como monto de cuota
                });
    }

    private String fmt(double monto) {
        return String.format("S/ %.2f", monto);
    }
}
