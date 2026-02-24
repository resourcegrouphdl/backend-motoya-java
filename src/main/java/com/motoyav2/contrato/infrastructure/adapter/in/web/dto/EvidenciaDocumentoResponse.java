package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

public record EvidenciaDocumentoResponse(
        String id,
        String tipoEvidencia,
        String urlEvidencia,
        String nombreArchivo,
        String tipoArchivo,
        Long tamanioBytes,
        String fechaSubida,
        String descripcion,
        String estadoValidacion,
        String validadoPor,
        String fechaValidacion,
        String observacionesValidacion
) {
}
