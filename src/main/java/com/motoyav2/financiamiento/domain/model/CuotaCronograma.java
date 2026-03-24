package com.motoyav2.financiamiento.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Detalle de una cuota individual dentro del cronograma de pagos.
 */
@Value
@Builder
public class CuotaCronograma {

    /** Número de cuota (1-based). */
    int numero;

    /** Saldo al inicio del período. */
    BigDecimal saldoInicial;

    /** Interés del período: saldoInicial * tasaQuincenal. */
    BigDecimal interes;

    /** Amortización al capital: cuota - interes. */
    BigDecimal amortizacion;

    /** Cuota total del período (interés + amortización). */
    BigDecimal cuota;

    /** Saldo al final del período (0 en la última cuota). */
    BigDecimal saldoFinal;
}
