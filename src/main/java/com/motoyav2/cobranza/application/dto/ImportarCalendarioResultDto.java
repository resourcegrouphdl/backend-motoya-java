package com.motoyav2.cobranza.application.dto;

public record ImportarCalendarioResultDto(
        int clientesDetectados,
        int casosCreados,
        int errores
) {}
