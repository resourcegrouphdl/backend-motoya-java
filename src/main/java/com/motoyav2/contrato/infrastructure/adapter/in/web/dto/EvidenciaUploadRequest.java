package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import com.motoyav2.contrato.domain.enums.TipoEvidencia;

public record EvidenciaUploadRequest(
    TipoEvidencia tipoEvidencia,
    String urlEvidencia,
    String nombreArchivo,
    String tipoArchivo,
    Integer tamanioBytes,
    String descripcion
) {
}
