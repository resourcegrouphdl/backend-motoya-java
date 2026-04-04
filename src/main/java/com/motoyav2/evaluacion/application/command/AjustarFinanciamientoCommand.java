package com.motoyav2.evaluacion.application.command;

import java.math.BigDecimal;

public record AjustarFinanciamientoCommand(
        String solicitudId,
        BigDecimal nuevaInicial,
        int nuevoPlazo,
        /**
         * Reservado para modo FORMAL (calculadora SBS).
         * En modo SIMPLIFICADO se ignora — la tasa se determina por el plazo.
         */
        BigDecimal tea,
        String usuarioId,
        String usuarioNombre
) {}
