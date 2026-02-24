package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

public record EvidenciaDocumentoRequest(
        String tipoEvidencia,
        String urlEvidencia,
        String nombreArchivo,
        String tipoArchivo,
        Long tamanioBytes,
        String descripcion
) {
}
