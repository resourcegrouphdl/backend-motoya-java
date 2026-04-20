package com.motoyav2.evaluacion.application.command;

import java.util.List;

public record AnalizarSentinelCommand(
        String clienteId,
        String tipoDocumento,
        String numeroDocumento,
        String nombreRazonSocial,
        List<FilaHistorial> filas
) {
    public record FilaHistorial(
            String fechaProceso,
            String semaforoActual,
            double score,
            String variacion,
            int    entidadesSBS,
            double deudaTotalSBS,
            double pctNormalSBS,
            String peorCalificacionSBS,
            double deudaVencidaSBS,
            int    diasVencSBS,
            double protestos,
            int    diasVencProtestos,
            double documentosImpagos,
            int    diasVencDocumentosImpagos,
            double deudaTributaria,
            int    diasVencDeudaTributaria
    ) {}
}
