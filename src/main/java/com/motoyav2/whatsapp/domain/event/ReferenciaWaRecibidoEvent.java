package com.motoyav2.whatsapp.domain.event;

/**
 * Publicado por el dispatcher cuando el teléfono entrante corresponde
 * a una referencia con estado wa_enviado en una solicitud activa.
 */
public record ReferenciaWaRecibidoEvent(
        String solicitudId,
        String refId,
        String fromPhone,
        String nombreReferencia,
        String text,
        String mediaType,
        String mediaUrl
) {}
