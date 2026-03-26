package com.motoyav2.calculadora.domain.model;

/**
 * Tipo de comisión de desembolso.
 *
 * FIXED      → monto fijo en soles.
 * PERCENTAGE → porcentaje del capital base (vehiculo + SOAT + gastos admin).
 */
public enum TipoComision {
    FIXED,
    PERCENTAGE
}