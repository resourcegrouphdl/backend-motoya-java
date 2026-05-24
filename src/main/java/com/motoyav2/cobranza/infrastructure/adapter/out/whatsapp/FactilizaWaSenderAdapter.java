package com.motoyav2.cobranza.infrastructure.adapter.out.whatsapp;

import com.motoyav2.cobranza.application.port.out.WhatsAppSenderPort;
import com.motoyav2.notifications.infrastructure.channel.whatsapp.MetaWhatsAppNotificationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class FactilizaWaSenderAdapter implements WhatsAppSenderPort {

    private final MetaWhatsAppNotificationAdapter meta;

    @Override
    public Mono<String> enviarTexto(String telefono, String texto) {
        return meta.sendText(telefono, texto)
                .onErrorResume(e -> {
                    log.warn("[WA-COBRANZA] Error enviando mensaje a {}: {}", telefono, e.getMessage());
                    return Mono.just("");
                });
    }
}