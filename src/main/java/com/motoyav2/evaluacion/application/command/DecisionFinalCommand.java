package com.motoyav2.evaluacion.application.command;

import com.motoyav2.evaluacion.domain.enums.Decision;

import java.math.BigDecimal;
import java.util.List;

public record DecisionFinalCommand(
        String solicitudId,
        Decision decision,
        String motivo,
        List<String> condiciones,
        BigDecimal inicialAjustada,     // nullable — si se ajusta la inicial
        Integer plazoAjustado,          // nullable — si se ajusta el plazo
        /**
         * Reservado para modo FORMAL (calculadora SBS).
         * En modo SIMPLIFICADO se ignora — la tasa se determina por el plazo.
         */
        BigDecimal tea,
        String fortalezasCaso,
        String debilidadesCaso,
        String evaluador,
        String usuarioId,
        String usuarioNombre
) {}
