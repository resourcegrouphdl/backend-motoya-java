package com.motoyav2.notifications.domain.port.in;

import reactor.core.publisher.Mono;

/**
 * Punto de entrada central para todos los mensajes entrantes de WhatsApp.
 * Recibe el payload parseado y lo enruta al handler correspondiente.
 */
public interface WhatsAppMessageDispatcher {

    /**
     * @param fromPhone Número de teléfono del remitente (puede incluir prefijo país)
     * @param text      Texto del mensaje (null si es media)
     * @param mediaType Tipo de media: "image" | "document" | "audio" | null si es texto
     * @param mediaUrl  URL del archivo de media de Factiliza (null si es texto)
     */
    Mono<Void> dispatch(String fromPhone, String text, String mediaType, String mediaUrl);
}
