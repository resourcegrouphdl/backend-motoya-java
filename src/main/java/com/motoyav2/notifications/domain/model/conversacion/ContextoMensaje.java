package com.motoyav2.notifications.domain.model.conversacion;

/**
 * Resultado del ResolutorContextoWhatsApp.
 * Identifica a quién pertenece un mensaje entrante.
 */
public record ContextoMensaje(
        String solicitudId,
        String participanteId,   // referenciaId, titularId o fiadorId según el rol
        String nombreParticipante,
        RolParticipante rol
) {}
