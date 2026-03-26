package com.motoyav2.calculadora.infrastructure.adapter.in.web.dto;

import com.motoyav2.calculadora.domain.model.FrequenciaPago;
import com.motoyav2.calculadora.domain.model.ResultadoSimulacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record SimularCreditoResponse(

        // Frecuencia
        FrequenciaPago frecuencia,
        int            numeroCuotas,

        // Capital
        BigDecimal precioVehiculo,
        BigDecimal soat,
        BigDecimal gastosAdministrativos,
        BigDecimal capitalBase,

        // Comisión
        BigDecimal comisionMonto,
        boolean    comisionFinanciada,

        // Financiamiento
        BigDecimal inicialMinima,
        BigDecimal inicialAplicada,
        BigDecimal montoFinanciar,
        BigDecimal efectivoNeto,

        // Tasas (SBS)
        BigDecimal tea,
        BigDecimal teaPorcentaje,
        BigDecimal tasaPeriodica,
        BigDecimal tasaPeriodicaPorcentaje,
        BigDecimal tcea,
        BigDecimal tceaPorcentaje,

        // Cuotas
        BigDecimal cuotaBase,
        BigDecimal cuotaTotalPromedio,

        // Totales
        BigDecimal totalIntereses,
        BigDecimal totalSeguro,
        BigDecimal totalAPagar,

        // Cronograma
        List<CuotaCronogramaDto> cronograma,

        // Metadata
        boolean inicialAjustadaPorTope,
        String  advertencia
) {
    public static SimularCreditoResponse from(ResultadoSimulacion r) {
        return new SimularCreditoResponse(
                r.getFrecuencia(),
                r.getNumeroCuotas(),
                r.getPrecioVehiculo(),
                r.getSoat(),
                r.getGastosAdministrativos(),
                r.getCapitalBase(),
                r.getComisionMonto(),
                r.isComisionFinanciada(),
                r.getInicialMinima(),
                r.getInicialAplicada(),
                r.getMontoFinanciar(),
                r.getEfectivoNeto(),
                r.getTea(),
                r.getTea().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                r.getTasaPeriodica(),
                r.getTasaPeriodica().multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP),
                r.getTcea(),
                r.getTcea().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                r.getCuotaBase(),
                r.getCuotaTotalPromedio(),
                r.getTotalIntereses(),
                r.getTotalSeguro(),
                r.getTotalAPagar(),
                r.getCronograma().stream().map(CuotaCronogramaDto::from).toList(),
                r.isInicialAjustadaPorTope(),
                r.getAdvertencia()
        );
    }
}