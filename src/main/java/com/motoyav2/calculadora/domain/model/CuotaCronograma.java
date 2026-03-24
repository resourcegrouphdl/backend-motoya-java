package com.motoyav2.calculadora.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuota individual del cronograma de pagos (Sistema Francés).
 * Estructura compatible con la Resolución SBS N° 11356-2008 (cronograma obligatorio).
 */
@Value
@Builder
public class CuotaCronograma {

    int numeroCuota;
    LocalDate fechaVencimiento;

    /** Saldo de capital al inicio del período */
    BigDecimal saldoInicial;

    /** Interés del período: saldoInicial × TEM */
    BigDecimal interes;

    /** Amortización de capital: cuota_base − interés */
    BigDecimal amortizacion;

    /** Seguro de desgravamen: saldoInicial × tasa_seguro_mensual */
    BigDecimal seguroDesgravamen;

    /** Cuota total: amortizacion + interes + seguro */
    BigDecimal cuotaTotal;

    /** Saldo de capital al final del período */
    BigDecimal saldoFinal;
}