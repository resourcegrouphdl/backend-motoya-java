package com.motoyav2.cobranza.domain.enums;

public enum TipoMovimiento {
    /** Deuda al aperturar el caso (+) */
    SALDO_INICIAL,
    /** Pago de cuota normal (-) */
    PAGO_CUOTA,
    /** Abono parcial (-) */
    PAGO_PARCIAL,
    /** Interés moratorio (+) */
    CARGO_MORA,
    /** Gastos de gestión (+) */
    CARGO_COBRANZA,
    /** Quita/condonación aprobada (-) */
    CONDONACION,
    /** Corrección administrativa (+/-) */
    AJUSTE_ADMIN,
    /** Nuevo capital tras acuerdo (+) */
    REFINANCIAMIENTO
}