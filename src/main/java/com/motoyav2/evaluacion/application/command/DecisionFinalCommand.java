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
         * TEA del crédito ajustado (ej: 0.60 = 60%).
         * Si es null se usa MotorFinancieroService.TEA_DEFAULT.
         */
        BigDecimal tea,
        String fortalezasCaso,
        String debilidadesCaso,
        String evaluador,
        String usuarioId,
        String usuarioNombre
) {}
