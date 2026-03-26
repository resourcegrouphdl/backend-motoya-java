package com.motoyav2.calculadora.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Configuración de la comisión de desembolso.
 *
 * financiada = true  → se suma al capital financiado (el cliente lo paga en cuotas).
 * financiada = false → se descuenta del efectivo recibido al momento del desembolso
 *                      (afecta el cálculo de TCEA conforme a transparencia SBS).
 */
@Value
@Builder
public class ComisionConfig {

    TipoComision tipo;

    /**
     * Si tipo == FIXED:      monto en soles (ej: 150.00).
     * Si tipo == PERCENTAGE: ratio sobre capital base (ej: 0.02 = 2%).
     */
    BigDecimal valor;

    /** true → comisión incluida en el préstamo; false → cobrada al desembolso. */
    boolean financiada;
}