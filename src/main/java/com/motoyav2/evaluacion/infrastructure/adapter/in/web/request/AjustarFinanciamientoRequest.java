package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AjustarFinanciamientoRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal nuevaInicial,
        @NotNull @Min(4) Integer nuevoPlazo,
        /**
         * TEA del crédito (decimal, ej: 0.60 = 60%).
         * Opcional — usa el valor por defecto si no se envía.
         */
        BigDecimal tea
) {}
