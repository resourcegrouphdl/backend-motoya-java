package com.motoyav2.whatsapp.domain.event;

/**
 * Publicado cuando el teléfono entrante no corresponde a ningún contexto
 * conocido (referencia, evaluación, cobranza).
 * El handler lo deriva a VoucherSueltoService.
 */
public record NumeroDesconocidoWaEvent(
        String fromPhone,
        String text,
        String mediaType,
        String mediaUrl
) {}
