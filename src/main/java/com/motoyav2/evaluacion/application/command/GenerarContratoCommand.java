package com.motoyav2.evaluacion.application.command;

import java.util.Map;

public record GenerarContratoCommand(
        String solicitudId,
        String usuarioId,
        Map<String, Object> camposAdicionales
) {}
