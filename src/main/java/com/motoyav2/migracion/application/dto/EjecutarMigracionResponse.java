package com.motoyav2.migracion.application.dto;

public record EjecutarMigracionResponse(
        String status,
        String contratoId,
        String message,
        String errorDetalle
) {}
