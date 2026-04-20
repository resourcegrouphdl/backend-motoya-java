package com.motoyav2.notifications.domain.model.conversacion;

import java.time.Instant;
import java.util.List;

public record ConversacionWa(
        String id,
        String solicitudId,
        String telefono,
        String nombreParticipante,
        RolParticipante rol,
        EstadoConversacion estadoConversacion,
        List<MensajeWa> mensajes,
        Instant ultimaActividad,
        Instant createdAt
) {}
