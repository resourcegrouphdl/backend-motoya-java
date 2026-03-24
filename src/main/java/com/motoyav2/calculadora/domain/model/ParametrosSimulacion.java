package com.motoyav2.calculadora.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Parámetros de entrada para una simulación de crédito.
 */
@Value
@Builder
public class ParametrosSimulacion {

    /** Precio del vehículo en soles */
    BigDecimal precioVehiculo;

    /** Inicial del cliente (null o menor que la mínima → se usa la mínima calculada) */
    BigDecimal inicial;

    /** Plazo en meses */
    int plazoMeses;

    /**
     * TEA a aplicar como override (null → usa la configuración del plazo).
     * Permite al administrador experimentar sin cambiar la config persistida.
     */
    BigDecimal teaOverride;

    /** Si true, incluye seguro de desgravamen en la cuota */
    boolean incluirSeguro;
}