package com.motoyav2.whatsapp.domain.event;

/**
 * Publicado por el controller de webhook cuando Meta informa un status update
 * (sent / delivered / read / failed).
 * El handler actualiza el campo estado en Firestore.
 */
public record EstadoMensajeActualizadoWaEvent(
        String wamid,
        String status,
        long timestampMs
) {}
