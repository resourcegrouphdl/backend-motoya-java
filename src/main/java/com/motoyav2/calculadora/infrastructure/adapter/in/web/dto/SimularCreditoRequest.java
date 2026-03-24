package com.motoyav2.calculadora.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Payload para simular un crédito.
 *
 * @param precioVehiculo  Precio del vehículo en soles (requerido, mín. S/500)
 * @param inicial         Inicial del cliente (opcional; null = usar mínima calculada)
 * @param plazoMeses      Plazo en meses (requerido, entre 1 y 60)
 * @param teaOverride     TEA a aplicar en decimal (opcional; null = usa configuración).
 *                        Permite experimentar sin alterar la config persistida.
 * @param incluirSeguro   Si incluir seguro de desgravamen (default false)
 */
public record SimularCreditoRequest(
        @NotNull @DecimalMin("500.00") BigDecimal precioVehiculo,
        BigDecimal inicial,
        @NotNull @Min(1) @Max(60) Integer plazoMeses,
        BigDecimal teaOverride,
        boolean incluirSeguro
) {}
