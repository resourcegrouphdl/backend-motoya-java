package com.motoyav2.evaluacion.application.command;

public record VerificarIdentidadCommand(
        String clienteId,
        String usuarioId,
        String usuarioNombre
) {}
