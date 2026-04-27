package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.EstrategiaPort;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.MensajeWhatsappPort;
import com.motoyav2.cobranza.application.port.out.PromesaPort;
import com.motoyav2.cobranza.domain.NivelMoraCalculadora;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EstrategiaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MensajeWhatsappDocument;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Ejecuta las estrategias de contacto activas configuradas en Firestore (cobranzas-estrategias).
 *
 * Solo procesa casos con diasMora > 0 (mora real).
 * El recordatorio del día 0 y el flujo base diario son responsabilidad de
 * CobranzaRecordatorioService.
 *
 * Cada WA enviado se registra en cobranzas-mensajes-whatsapp (ventana 360°).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EstrategiaAutomaticaService {

    private final EstrategiaPort      estrategiaPort;
    private final CasoCobranzaPort    casoPort;
    private final PromesaPort         promesaPort;
    private final EventoCobranzaPort  eventoPort;
    private final MensajeWhatsappPort mensajePort;
    private final NotificationFacade  notificationFacade;

    // ─────────────────────────────────────────────────────────────────────────

    public Mono<Integer> ejecutarEstrategiasActivas() {
        LocalDate hoy        = LocalDate.now(NivelMoraCalculadora.LIMA);
        LocalTime horaActual = LocalTime.now(NivelMoraCalculadora.LIMA);

        log.info("[ESTRATEGIA] Evaluando estrategias activas — hora Lima: {}", horaActual);

        return estrategiaPort.findAll()
                .filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .filter(e -> "WHATSAPP".equals(e.getCanal()))
                .filter(e -> estaEnHorario(e, horaActual))
                .flatMap(estrategia -> ejecutarEstrategia(estrategia, hoy), 2)
                .reduce(0, Integer::sum)
                .doOnNext(total -> log.info("[ESTRATEGIA] Ciclo completado — casos contactados: {}", total));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Mono<Integer> ejecutarEstrategia(EstrategiaDocument estrategia, LocalDate hoy) {
        log.debug("[ESTRATEGIA] Evaluando: {} (nivel={} diasMora={}-{})",
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
                            log.warn("[ESTRATEGIA] Error en caso {}: {}",
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

        int diasMora = NivelMoraCalculadora.diasMora(caso, hoy);
        double mora  = NivelMoraCalculadora.moraSoles(diasMora);
        double cuota = NivelMoraCalculadora.montoPrimeraCuotaImpaga(caso, hoy);
        double total = cuota + mora;

        caso.setUltimoRecordatorioMora(new Date());
        caso.setActualizadoEn(new Date());

        EventoCobranzaDocument evento = EventoCobranzaDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(caso.getContratoId())
                .tipo("ESTRATEGIA_DISPARADA")
                .payload(Map.of(
                        "estrategiaId",     estrategia.getId(),
                        "estrategiaNombre", estrategia.getNombre(),
                        "nivel",            estrategia.getNivel() != null ? estrategia.getNivel() : "",
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
                        NivelMoraCalculadora.fmt(cuota),
                        String.valueOf(diasMora),
                        NivelMoraCalculadora.fmt(mora),
                        NivelMoraCalculadora.fmt(total))
                .onErrorResume(e -> {
                    log.warn("[ESTRATEGIA] WA fallido {}: {}", caso.getContratoId(), e.getMessage());
                    return Mono.empty();
                });

        MensajeWhatsappDocument msg = MensajeWhatsappDocument.builder()
                .id(UUID.randomUUID().toString())
                .contratoId(caso.getContratoId())
                .clienteNombre(cliente)
                .telefono(telefono)
                .plantillaNombre("Estrategia: " + estrategia.getNombre())
                .estado("ENVIADO")
                .direction("OUTBOUND")
                .automatico(true)
                .estrategiaId(estrategia.getId())
                .enviadoEn(new Date())
                .storeId(caso.getStoreId())
                .build();

        return casoPort.save(caso)
                .then(eventoPort.append(caso.getContratoId(), evento))
                .then(wa)
                .then(mensajePort.save(msg).then())
                .onErrorResume(e -> {
                    log.warn("[ESTRATEGIA] Error guardando en 360 {}: {}", caso.getContratoId(), e.getMessage());
                    return Mono.empty();
                });
    }

    // ── Predicados ───────────────────────────────────────────────────────────

    private Mono<Boolean> debeContactar(CasoCobranzaDocument caso, EstrategiaDocument estrategia) {
        return promesaPort.findVigente(caso.getContratoId())
                .hasElement()
                .map(tienePromesa -> {
                    if (tienePromesa) return false;
                    Date ultimo = caso.getUltimoRecordatorioMora();
                    if (ultimo == null) return true;
                    LocalDate ultimaFecha = ultimo.toInstant()
                            .atZone(NivelMoraCalculadora.LIMA).toLocalDate();
                    int frecuencia = estrategia.getFrecuenciaDias() != null
                            ? estrategia.getFrecuenciaDias() : 3;
                    return (int) ChronoUnit.DAYS.between(ultimaFecha, LocalDate.now(NivelMoraCalculadora.LIMA))
                            >= frecuencia;
                });
    }

    private boolean coincideNivel(CasoCobranzaDocument caso, EstrategiaDocument estrategia) {
        if (estrategia.getNivel() == null) return true;
        return estrategia.getNivel().equals(caso.getNivelEstrategia());
    }

    private boolean diasMoraEnRango(CasoCobranzaDocument caso,
                                     EstrategiaDocument estrategia,
                                     LocalDate hoy) {
        int diasMora = NivelMoraCalculadora.diasMora(caso, hoy);
        if (diasMora <= 0) return false;  // estrategias solo para mora real (día 1+)
        if (estrategia.getDiasMoraDesde() != null && diasMora < estrategia.getDiasMoraDesde()) return false;
        if (estrategia.getDiasMoraHasta() != null && diasMora > estrategia.getDiasMoraHasta()) return false;
        return true;
    }

    private boolean estaEnHorario(EstrategiaDocument estrategia, LocalTime ahora) {
        try {
            if (estrategia.getHorarioDesde() != null) {
                if (ahora.isBefore(LocalTime.parse(estrategia.getHorarioDesde()))) return false;
            }
            if (estrategia.getHorarioHasta() != null) {
                if (ahora.isAfter(LocalTime.parse(estrategia.getHorarioHasta()))) return false;
            }
        } catch (Exception e) {
            log.debug("[ESTRATEGIA] Horario inválido en estrategia {}", estrategia.getId());
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
}