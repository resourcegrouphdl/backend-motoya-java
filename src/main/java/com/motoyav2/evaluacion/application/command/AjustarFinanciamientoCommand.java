package com.motoyav2.evaluacion.application.command;

import java.math.BigDecimal;

public record AjustarFinanciamientoCommand(
        String solicitudId,
        BigDecimal nuevaInicial,
        int nuevoPlazo,
        /**
         * TEA del crédito ajustado (ej: 0.60 = 60%).
         * Si es null se usa MotorFinancieroService.TEA_DEFAULT.
         */
        BigDecimal tea,
        String usuarioId,
        String usuarioNombre
) {}
