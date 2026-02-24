package com.motoyav2.cobranza.application.dto;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MovimientoDocument;

import java.util.List;

/**
 * Historial de movimientos de deuda + resumen financiero.
 */
public record MovimientosResumenDto(
        List<MovimientoDocument> movimientos,
        Double totalCargos,
        Double totalAbonos,
        Double saldoNeto
) {}
