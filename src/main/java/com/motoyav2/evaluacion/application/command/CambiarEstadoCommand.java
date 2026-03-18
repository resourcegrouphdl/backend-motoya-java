package com.motoyav2.evaluacion.application.command;

import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;

public record CambiarEstadoCommand(
        String solicitudId,
        EstadoSolicitud nuevoEstado,
        String usuarioId,
        String usuarioNombre,
        String motivo
) {}
