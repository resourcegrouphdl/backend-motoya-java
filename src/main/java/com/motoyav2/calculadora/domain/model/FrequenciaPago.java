package com.motoyav2.calculadora.domain.model;

/**
 * Frecuencias de pago soportadas.
 *
 * Solo se permiten periodos homogéneos (condición necesaria para
 * estabilidad matemática y validez del cálculo de TCEA).
 */
public enum FrequenciaPago {
    /** Pagos cada 7 días exactos. tasa = (1+TEA)^(7/360) − 1 */
    WEEKLY,

    /** Pagos mensuales. tasa = (1+TEA)^(1/12) − 1 */
    MONTHLY
}