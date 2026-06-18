package com.motoyav2.whatsapp.application.service;

import com.motoyav2.cobranza.application.port.in.ActualizarEstadoMensajeUseCase;
import com.motoyav2.whatsapp.domain.event.EstadoMensajeActualizadoWaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class EstadoMensajeWhatsAppEventHandler {

    private final ActualizarEstadoMensajeUseCase actualizarEstado;

    @Async("whatsappEventExecutor")
    @EventListener
    public void handle(EstadoMensajeActualizadoWaEvent event) {
        log.info("[WA-HANDLER-STATUS] wamid={} status={}", event.wamid(), event.status());
        actualizarEstado.ejecutar(event.wamid(), event.status(), new Date(event.timestampMs()))
                .subscribe(null, e -> log.warn("[WA-HANDLER-STATUS] Error actualizando estado wamid={}: {}",
                        event.wamid(), e.getMessage()));
    }
}
