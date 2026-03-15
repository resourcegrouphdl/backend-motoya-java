package com.motoyav2.migracion.application.dto;

public record ImportarCalendarResponse(
        String status,
        int clientesDetectados,
        int registrosCreados,
        int duplicadosIgnorados,
        String message
) {}
