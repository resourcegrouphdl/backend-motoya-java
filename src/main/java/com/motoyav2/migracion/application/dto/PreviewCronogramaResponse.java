package com.motoyav2.migracion.application.dto;

import java.util.List;

public record PreviewCronogramaResponse(
        int totalCuotas,
        double montoCuota,
        double capitalTotal,
        int cuotasPagadas,
        int cuotasPendientes,
        int diasMoraEstimados,
        double saldoEstimado,
        List<CuotaPreviewDto> cronograma
) {
    public record CuotaPreviewDto(
            int cuota,
            String fechaVencimiento,
            /** PAGADA | VENCIDA | VIGENTE */
            String estado,
            double monto
    ) {}
}
