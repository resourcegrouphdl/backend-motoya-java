package com.motoyav2.whatsapp.application.service;

import com.motoyav2.cobranza.application.service.VoucherSueltoService;
import com.motoyav2.whatsapp.domain.event.NumeroDesconocidoWaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NumeroDesconocidoWhatsAppEventHandler {

    private final VoucherSueltoService voucherSueltoService;

    @Async("whatsappEventExecutor")
    @EventListener
    public void handle(NumeroDesconocidoWaEvent event) {
        log.info("[WA-HANDLER-UNK] Número desconocido phone={} — derivando a VoucherSuelto", event.fromPhone());
        voucherSueltoService.manejarMensajeDesconocido(
                event.fromPhone(), event.text(), event.mediaType(), event.mediaUrl()
        ).subscribe(null, e -> log.warn("[WA-HANDLER-UNK] Error: {}", e.getMessage()));
    }
}
