package com.motoyav2.whatsapp.domain.event;

/**
 * Publicado por el dispatcher cuando el teléfono entrante corresponde
 * a un cliente con caso de cobranza activo.
 */
public record CobranzaWaRecibidoEvent(
        String contratoId,
        String storeId,
        String clienteNombre,
        String agenteAsignadoId,
        String fromPhone,
        String text,
        String mediaType,
        String mediaUrl
) {}
