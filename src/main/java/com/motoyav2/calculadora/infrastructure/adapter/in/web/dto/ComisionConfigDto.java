package com.motoyav2.calculadora.infrastructure.adapter.in.web.dto;

import com.motoyav2.calculadora.domain.model.ComisionConfig;
import com.motoyav2.calculadora.domain.model.TipoComision;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO para la comisión de desembolso.
 *
 * valor:
 *   - FIXED      → monto en soles
 *   - PERCENTAGE → ratio decimal (ej: 0.02 = 2% del capital base)
 */
public record ComisionConfigDto(
        @NotNull TipoComision tipo,
        @NotNull @DecimalMin("0.00") BigDecimal valor,
        boolean financiada
) {
    public ComisionConfig toDomain() {
        return ComisionConfig.builder()
                .tipo(tipo)
                .valor(valor)
                .financiada(financiada)
                .build();
    }

    public static ComisionConfigDto from(ComisionConfig c) {
        if (c == null) return new ComisionConfigDto(TipoComision.FIXED, BigDecimal.ZERO, false);
        return new ComisionConfigDto(c.getTipo(), c.getValor(), c.isFinanciada());
    }
}