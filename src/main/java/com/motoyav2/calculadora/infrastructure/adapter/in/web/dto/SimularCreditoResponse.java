package com.motoyav2.calculadora.infrastructure.adapter.in.web.dto;

import com.motoyav2.calculadora.domain.model.ResultadoSimulacion;

import java.math.BigDecimal;
import java.util.List;

public record SimularCreditoResponse(

        // Precio
        BigDecimal precioVehiculo,
        BigDecimal gastosAdministrativos,
        BigDecimal precioTotal,

        // Financiamiento
        BigDecimal inicialMinima,
        BigDecimal inicialAplicada,
        BigDecimal montoFinanciar,
        int        plazoMeses,
        BigDecimal comisionDesembolso,

        // Tasas (SBS)
        BigDecimal tea,
        BigDecimal teaPorcentaje,
        BigDecimal tem,
        BigDecimal temPorcentaje,
        BigDecimal tcea,
        BigDecimal tceaPorcentaje,

        // Cuotas
        BigDecimal cuotaBaseMensual,
        BigDecimal cuotaTotalMensual,

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
                r.getPrecioVehiculo(),
                r.getGastosAdministrativos(),
                r.getPrecioTotal(),
                r.getInicialMinima(),
                r.getInicialAplicada(),
                r.getMontoFinanciar(),
                r.getPlazoMeses(),
                r.getComisionDesembolso(),
                r.getTea(),
                r.getTea().multiply(BigDecimal.valueOf(100)).setScale(2, java.math.RoundingMode.HALF_UP),
                r.getTem(),
                r.getTem().multiply(BigDecimal.valueOf(100)).setScale(4, java.math.RoundingMode.HALF_UP),
                r.getTcea(),
                r.getTcea().multiply(BigDecimal.valueOf(100)).setScale(2, java.math.RoundingMode.HALF_UP),
                r.getCuotaBaseMensual(),
                r.getCuotaTotalMensual(),
                r.getTotalIntereses(),
                r.getTotalSeguro(),
                r.getTotalAPagar(),
                r.getCronograma().stream().map(CuotaCronogramaDto::from).toList(),
                r.isInicialAjustadaPorTope(),
                r.getAdvertencia()
        );
    }
}
