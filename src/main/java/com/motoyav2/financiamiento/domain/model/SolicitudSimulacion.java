package com.motoyav2.financiamiento.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Objeto de valor: parámetros de entrada para una simulación de crédito.
 */
@Value
@Builder
public class SolicitudSimulacion {

    /** Precio del vehículo (sin gastos). */
    BigDecimal precioVehiculo;

    /** Cuota inicial a pagar al contado. */
    BigDecimal cuotaInicial;

    /** Número de cuotas quincenales (ej: 16, 20, 24 o cualquier plazo). */
    int numeroCuotas;

    /**
     * Tasa Efectiva Anual expresada como decimal.
     * Ejemplo: 0.60 = 60% TEA
     */
    BigDecimal tea;

    /**
     * Gastos administrativos. Si es null se usa el valor por defecto de negocio (S/ 890).
     */
    BigDecimal gastosAdministrativos;
}
