package com.motoyav2.evaluacion.application.command;

public record AsignarAsesorCommand(
        String solicitudId,
        String asesorId,
        String asesorNombre,
        String asesorEmail,
        String usuarioId,
        String usuarioNombre
) {}
