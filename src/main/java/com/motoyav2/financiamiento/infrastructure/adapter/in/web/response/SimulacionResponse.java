package com.motoyav2.financiamiento.infrastructure.adapter.in.web.response;

import com.motoyav2.financiamiento.domain.model.ResultadoSimulacion;

import java.math.BigDecimal;
import java.util.List;

public record SimulacionResponse(
        // Inputs echoed
        BigDecimal precioVehiculo,
        BigDecimal cuotaInicial,
        BigDecimal gastosAdministrativos,
        int        numeroCuotas,
        BigDecimal tea,

        // Calculados
        BigDecimal montoFinanciado,
        BigDecimal tasaQuincenal,
        BigDecimal cuotaQuincenal,
        BigDecimal totalIntereses,
        BigDecimal totalPagar,

        /**
         * Costo Efectivo Anual en porcentaje.
         * Ej: 72.35 = 72.35% TCEA
         */
        BigDecimal tcea,

        List<CuotaCronogramaResponse> cronograma
) {
    public static SimulacionResponse from(ResultadoSimulacion r) {
        return new SimulacionResponse(
                r.getPrecioVehiculo(),
                r.getCuotaInicial(),
                r.getGastosAdministrativos(),
                r.getNumeroCuotas(),
                r.getTea(),
                r.getMontoFinanciado(),
                r.getTasaQuincenal(),
                r.getCuotaQuincenal(),
                r.getTotalIntereses(),
                r.getTotalPagar(),
                r.getTcea(),
                r.getCronograma().stream()
                        .map(CuotaCronogramaResponse::from)
                        .toList()
        );
    }
}
