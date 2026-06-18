package com.motoyav2.whatsapp.application.service;

import com.motoyav2.evaluacion.domain.port.in.ProcesarPreferenciaEntrevistaUseCase;
import com.motoyav2.notifications.domain.model.conversacion.DireccionMensaje;
import com.motoyav2.notifications.domain.model.conversacion.RolParticipante;
import com.motoyav2.notifications.domain.model.conversacion.TipoMensajeWa;
import com.motoyav2.notifications.domain.port.in.RegistrarMensajeConversacionUseCase;
import com.motoyav2.whatsapp.domain.event.EvaluacionParticipanteWaRecibidoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluacionWhatsAppEventHandler {

    private final ProcesarPreferenciaEntrevistaUseCase procesarPreferencia;
    private final RegistrarMensajeConversacionUseCase  registrarMensaje;

    @Async("whatsappEventExecutor")
    @EventListener
    public void handle(EvaluacionParticipanteWaRecibidoEvent event) {
        log.info("[WA-HANDLER-EVAL] Procesando | solicitudId={} esFiador={} phone={}",
                event.solicitudId(), event.esFiador(), event.fromPhone());

        if (event.text() != null) {
            procesarPreferencia.procesar(event.solicitudId(), event.fromPhone(), event.text(), event.esFiador())
                    .subscribe(null, e -> log.warn("[WA-HANDLER-EVAL] Error procesando preferencia: {}", e.getMessage()));

        } else if (event.mediaUrl() != null) {
            RolParticipante rol = event.esFiador() ? RolParticipante.FIADOR : RolParticipante.TITULAR;
            TipoMensajeWa tipo = detectarTipo(event.mediaType());
            String contenido = tipo.name() + " recibido"
                    + (event.mediaType() != null ? " (" + event.mediaType() + ")" : "");
            registrarMensaje.registrar(
                    event.solicitudId(), event.fromPhone(),
                    event.nombreParticipante() != null ? event.nombreParticipante() : "Participante",
                    rol, DireccionMensaje.INBOUND, tipo,
                    contenido, event.mediaUrl(), null, null, null
            ).subscribe(null, e -> log.warn("[WA-HANDLER-EVAL] Error registrando media: {}", e.getMessage()));
        }
    }

    private TipoMensajeWa detectarTipo(String mediaType) {
        if (mediaType == null) return TipoMensajeWa.DESCONOCIDO;
        return switch (mediaType.toLowerCase()) {
            case "image"    -> TipoMensajeWa.IMAGEN;
            case "document" -> TipoMensajeWa.DOCUMENTO;
            case "audio"    -> TipoMensajeWa.AUDIO;
            default         -> TipoMensajeWa.DESCONOCIDO;
        };
    }
}
