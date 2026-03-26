package com.motoyav2.calculadora.infrastructure.adapter.in.web.dto;

import com.motoyav2.calculadora.domain.model.FrequenciaPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Payload para simular un crédito.
 *
 * @param precioVehiculo  Precio del vehículo en soles (requerido, mín. S/500)
 * @param soat            SOAT del vehículo en soles (financiable; null = 0)
 * @param inicial         Inicial del cliente (null = usar mínima calculada)
 * @param numeroCuotas    Número de cuotas (semanas si WEEKLY, meses si MONTHLY; 1-260)
 * @param frecuencia      WEEKLY | MONTHLY
 * @param teaOverride     TEA en decimal (null = usa configuración del plazo)
 * @param incluirSeguro   Si incluir seguro de desgravamen
 * @param comision        Comisión de desembolso (null = usa la configurada por defecto)
 */
public record SimularCreditoRequest(
        @NotNull @DecimalMin("500.00") BigDecimal precioVehiculo,
        @DecimalMin("0.00") BigDecimal soat,
        BigDecimal inicial,
        @NotNull @Min(1) @Max(260) Integer numeroCuotas,
        @NotNull FrequenciaPago frecuencia,
        BigDecimal teaOverride,
        boolean incluirSeguro,
        @Valid ComisionConfigDto comision
) {}