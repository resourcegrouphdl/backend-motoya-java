package com.motoyav2.evaluacion.application.command;

import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;

import java.util.Map;

public record EvaluarDocumentosCommand(
        String solicitudId,
        Double scoreDocumental,
        String observaciones,
        EstadoSolicitud nuevoEstado,
        String usuarioId,
        String usuarioNombre,
        Map<String, EvaluacionDocumentoData> evaluacionDocumentos,  // nullable
        /** ID del cliente a actualizar. Si es null se usa titularId de la solicitud. */
        String clienteId                                            // nullable
) {
    public record EvaluacionDocumentoData(String estado, String observaciones) {}
}
