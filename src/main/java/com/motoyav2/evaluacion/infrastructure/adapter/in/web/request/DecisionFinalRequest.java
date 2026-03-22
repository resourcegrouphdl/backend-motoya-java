package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record DecisionFinalRequest(
        @NotNull(message = "decision es requerida")
        String decision,        // aprobado | rechazado | condicional
        @NotBlank(message = "motivo es requerido")
        String motivo,
        List<String> condiciones,
        BigDecimal inicialAjustada,
        Integer plazoAjustado,
        /**
         * TEA del crédito ajustado (decimal, ej: 0.60 = 60%).
         * Opcional — usa el valor por defecto si no se envía.
         */
        BigDecimal tea,
        String fortalezasCaso,
        String debilidadesCaso,
        @NotBlank(message = "evaluador es requerido")
        String evaluador
) {}
