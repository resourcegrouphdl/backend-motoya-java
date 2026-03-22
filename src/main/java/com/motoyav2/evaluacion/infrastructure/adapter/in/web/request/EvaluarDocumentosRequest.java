package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

import java.util.Map;

public record EvaluarDocumentosRequest(
        Double scoreDocumental,
        String observaciones,
        String nuevoEstado,                                    // nullable
        Map<String, EvaluacionDocumentoDto> evaluacionDocumentos,  // nullable
        /** ID del cliente a actualizar. Si es null se usa titularId de la solicitud. */
        String clienteId                                       // nullable
) {
    public record EvaluacionDocumentoDto(String estado, String observaciones) {}
}
