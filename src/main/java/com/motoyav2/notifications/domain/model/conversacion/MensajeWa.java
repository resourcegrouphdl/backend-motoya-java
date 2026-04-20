package com.motoyav2.notifications.domain.model.conversacion;

import java.time.Instant;

public record MensajeWa(
        String id,
        DireccionMensaje direccion,
        TipoMensajeWa tipo,
        String contenido,
        String mediaUrl,
        String storageRef,
        String enviadorNombre,
        String claudeClasificacion,
        Double claudeConfianza,
        Instant timestamp
) {}
