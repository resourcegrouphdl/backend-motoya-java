package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Entrada del ledger contable. Cada evento financiero (pago cliente,
 * pago a tienda, comisión) genera un MovimientoContable.
 * Persiste en contabilidad_movimientos/{referenciaId}.
 */
@Value
@Builder
public class MovimientoContable {
    String id;
    TipoMovimientoContable tipo;
    String contratoId;
    String tiendaId;
    /** voucherId | facturaId+pagoId | pagoComisionId */
    String referenciaId;
    /** Primer día de la quincena: yyyy-MM-dd */
    String periodo;
    // Campos para INGRESO_CUOTA
    double montoTotal;
    double montoCapital;
    double montoInteres;
    // Campos para COSTO_TIENDA / COSTO_COMISION
    double montoCosto;
    Instant creadoEn;
}
