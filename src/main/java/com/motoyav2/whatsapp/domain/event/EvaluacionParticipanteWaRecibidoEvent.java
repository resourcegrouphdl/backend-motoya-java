package com.motoyav2.whatsapp.domain.event;

/**
 * Publicado por el dispatcher cuando el teléfono entrante corresponde
 * al titular o fiador de una solicitud activa de evaluación.
 */
public record EvaluacionParticipanteWaRecibidoEvent(
        String solicitudId,
        String fromPhone,
        String nombreParticipante,
        String text,
        String mediaType,
        String mediaUrl,
        boolean esFiador
) {}
