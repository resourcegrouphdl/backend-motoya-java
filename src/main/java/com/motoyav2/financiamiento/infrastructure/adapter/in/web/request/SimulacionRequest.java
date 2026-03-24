package com.motoyav2.financiamiento.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request para simular un crédito.
 *
 * <pre>
 * {
 *   "precioVehiculo": 5000,
 *   "cuotaInicial": 1000,
 *   "numeroCuotas": 24,
 *   "tea": 0.60,
 *   "gastosAdministrativos": 890   // opcional, default: 890
 * }
 * </pre>
 */
public record SimulacionRequest(

        @NotNull(message = "precioVehiculo es requerido")
        @DecimalMin(value = "1.00", message = "precioVehiculo debe ser mayor a cero")
        BigDecimal precioVehiculo,

        @NotNull(message = "cuotaInicial es requerida")
        @DecimalMin(value = "0.00", message = "cuotaInicial no puede ser negativa")
        BigDecimal cuotaInicial,

        @NotNull(message = "numeroCuotas es requerido")
        @Min(value = 1, message = "numeroCuotas debe ser al menos 1")
        Integer numeroCuotas,

        @NotNull(message = "tea es requerida (ej: 0.60 para 60% TEA)")
        @DecimalMin(value = "0.001", message = "tea debe ser mayor a cero")
        BigDecimal tea,

        /** Opcional — usa 890 si no se envía. */
        BigDecimal gastosAdministrativos
) {}
