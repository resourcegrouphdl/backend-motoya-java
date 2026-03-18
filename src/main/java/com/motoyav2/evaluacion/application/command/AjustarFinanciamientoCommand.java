package com.motoyav2.evaluacion.application.command;

import java.math.BigDecimal;

public record AjustarFinanciamientoCommand(
        String solicitudId,
        BigDecimal nuevaInicial,
        int nuevoPlazo,
        String usuarioId,
        String usuarioNombre
) {}
