package com.motoyav2.whatsapp.application.service;

import com.motoyav2.evaluacion.domain.port.in.ProcesarRespuestaReferenciaUseCase;
import com.motoyav2.notifications.domain.model.conversacion.DireccionMensaje;
import com.motoyav2.notifications.domain.model.conversacion.RolParticipante;
import com.motoyav2.notifications.domain.model.conversacion.TipoMensajeWa;
import com.motoyav2.notifications.domain.port.in.RegistrarMensajeConversacionUseCase;
import com.motoyav2.whatsapp.domain.event.ReferenciaWaRecibidoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenciaWhatsAppEventHandler {

    private final ProcesarRespuestaReferenciaUseCase procesarReferencia;
    private final RegistrarMensajeConversacionUseCase registrarMensaje;

    @Async("whatsappEventExecutor")
    @EventListener
    public void handle(ReferenciaWaRecibidoEvent event) {
        log.info("[WA-HANDLER-REF] Procesando | refId={} phone={}", event.refId(), event.fromPhone());

        if (event.text() != null) {
            registrarMensaje.registrar(
                    event.solicitudId(), event.fromPhone(), event.nombreReferencia(),
                    RolParticipante.REFERENCIA,
                    DireccionMensaje.INBOUND, TipoMensajeWa.TEXTO,
                    event.text(), null, null, null, null
            ).subscribe(null, e -> log.warn("[WA-HANDLER-REF] Error registrando msg: {}", e.getMessage()));

            procesarReferencia.ejecutar(event.fromPhone(), event.text())
                    .subscribe(null, e -> log.warn("[WA-HANDLER-REF] Error procesando respuesta: {}", e.getMessage()));

        } else if (event.mediaUrl() != null) {
            TipoMensajeWa tipo = detectarTipo(event.mediaType());
            String contenido = tipo.name() + " recibido"
                    + (event.mediaType() != null ? " (" + event.mediaType() + ")" : "");
            registrarMensaje.registrar(
                    event.solicitudId(), event.fromPhone(), event.nombreReferencia(),
                    RolParticipante.REFERENCIA,
                    DireccionMensaje.INBOUND, tipo,
                    contenido, event.mediaUrl(), null, null, null
            ).subscribe(null, e -> log.warn("[WA-HANDLER-REF] Error registrando media: {}", e.getMessage()));
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
