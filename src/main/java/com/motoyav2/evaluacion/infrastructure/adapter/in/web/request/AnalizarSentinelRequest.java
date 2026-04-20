package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

import com.motoyav2.evaluacion.application.command.AnalizarSentinelCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AnalizarSentinelRequest(
        @NotBlank String tipoDocumento,
        @NotBlank String numeroDocumento,
        @NotBlank String nombreRazonSocial,
        @NotEmpty List<FilaHistorialRequest> filas
) {
    public record FilaHistorialRequest(
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

    public AnalizarSentinelCommand toCommand(String clienteId) {
        List<AnalizarSentinelCommand.FilaHistorial> filasCmd = filas.stream()
                .map(f -> new AnalizarSentinelCommand.FilaHistorial(
                        f.fechaProceso(), f.semaforoActual(), f.score(), f.variacion(),
                        f.entidadesSBS(), f.deudaTotalSBS(), f.pctNormalSBS(),
                        f.peorCalificacionSBS(), f.deudaVencidaSBS(), f.diasVencSBS(),
                        f.protestos(), f.diasVencProtestos(),
                        f.documentosImpagos(), f.diasVencDocumentosImpagos(),
                        f.deudaTributaria(), f.diasVencDeudaTributaria()
                ))
                .toList();
        return new AnalizarSentinelCommand(clienteId, tipoDocumento, numeroDocumento,
                nombreRazonSocial, filasCmd);
    }
}
