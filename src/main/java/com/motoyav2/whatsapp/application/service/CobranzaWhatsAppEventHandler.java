package com.motoyav2.whatsapp.application.service;

import com.motoyav2.cobranza.application.port.in.ProcesarVoucherWhatsappUseCase;
import com.motoyav2.cobranza.application.port.out.AlertaCobranzaPort;
import com.motoyav2.cobranza.application.service.InboundMensajeWaService;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AlertaCobranzaDocument;
import com.motoyav2.notifications.infrastructure.facade.NotificationFacade;
import com.motoyav2.whatsapp.domain.event.CobranzaWaRecibidoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CobranzaWhatsAppEventHandler {

    private final InboundMensajeWaService        inboundSvc;
    private final ProcesarVoucherWhatsappUseCase procesarVoucherWhatsapp;
    private final NotificationFacade             notificationFacade;
    private final AlertaCobranzaPort             alertaPort;

    @Value("${app.notifications.cobranzas.enabled:false}")
    private boolean notificacionesEnabled;

    @Async("whatsappEventExecutor")
    @EventListener
    public void handle(CobranzaWaRecibidoEvent event) {
        log.info("[WA-HANDLER-COB] Procesando | contratoId={} phone={}", event.contratoId(), event.fromPhone());

        if (event.text() != null) {
            inboundSvc.registrarMensajeEntrante(
                            event.contratoId(), event.clienteNombre(), event.fromPhone(),
                            null, event.text(), new Date())
                    .then(inboundSvc.actualizarRespuestaCliente(event.contratoId()))
                    .subscribe(null, e -> log.warn("[WA-HANDLER-COB] Error guardando texto: {}", e.getMessage()));

            crearAlertaInbound(event, "Mensaje de texto recibido");

            if (notificacionesEnabled) {
                String nombre = event.clienteNombre() != null ? event.clienteNombre() : "Cliente";
                notificationFacade.notificarAutorespuestaCobranza(event.contratoId(), event.fromPhone(), nombre)
                        .subscribe(null, e -> log.warn("[WA-HANDLER-COB] Error notificando autorespuesta: {}", e.getMessage()));
            } else {
                log.info("[WA-HANDLER-COB] Autorespuesta WA desactivada (app.notifications.cobranzas.enabled=false) — contratoId={}", event.contratoId());
            }

        } else if (event.mediaUrl() != null) {
            inboundSvc.registrarMediaEntrante(
                            event.contratoId(), event.clienteNombre(), event.fromPhone(),
                            event.mediaUrl(), event.mediaType(), new Date())
                    .flatMap(mensajeId -> {
                        inboundSvc.actualizarRespuestaCliente(event.contratoId())
                                .subscribe(null, e -> log.warn("[WA-HANDLER-COB] Error actualizando respuesta: {}", e.getMessage()));
                        crearAlertaInbound(event, "Imagen recibida — posible comprobante");
                        return procesarVoucherWhatsapp.procesar(
                                event.contratoId(), event.storeId(),
                                event.clienteNombre(), event.fromPhone(),
                                event.mediaUrl(), event.mediaType(), mensajeId);
                    })
                    .subscribe(null, e -> log.warn("[WA-HANDLER-COB] Error procesando media: {}", e.getMessage()));
        }
    }

    private void crearAlertaInbound(CobranzaWaRecibidoEvent event, String descripcion) {
        if (event.agenteAsignadoId() == null || event.agenteAsignadoId().isBlank()) return;
        AlertaCobranzaDocument alerta = AlertaCobranzaDocument.builder()
                .id(UUID.randomUUID().toString())
                .tipo("MENSAJE_INBOUND_WHATSAPP")
                .nivel("INFO")
                .titulo("Mensaje de " + (event.clienteNombre() != null ? event.clienteNombre() : "cliente"))
                .descripcion(descripcion)
                .contratoId(event.contratoId())
                .clienteNombre(event.clienteNombre())
                .storeId(event.storeId())
                .agenteId(event.agenteAsignadoId())
                .accionSugerida("Abrir chat y responder al cliente")
                .accionRuta("/cobranzas/vista360/" + event.contratoId())
                .leida(false)
                .descartada(false)
                .creadoEn(new Date())
                .expiraEn(new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000))
                .build();
        alertaPort.save(alerta)
                .subscribe(null, e -> log.warn("[WA-HANDLER-COB] Error creando alerta: {}", e.getMessage()));
    }
}
