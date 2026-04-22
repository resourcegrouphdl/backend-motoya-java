package com.motoyav2.cobranza.application.port.out;

import reactor.core.publisher.Mono;

public interface WhatsAppSenderPort {
    /** Envía texto y retorna el wamid asignado, o vacío en caso de error no fatal. */
    Mono<String> enviarTexto(String telefono, String texto);
}
