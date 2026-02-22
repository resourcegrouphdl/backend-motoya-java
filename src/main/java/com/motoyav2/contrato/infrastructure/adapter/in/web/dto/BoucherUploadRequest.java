package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import jakarta.annotation.Nullable;

public record BoucherUploadRequest(
    @Nullable String id,
    String urlDocumento,
    String nombreArchivo,
    String tipoArchivo,
    Integer tamanioBytes
) {
}
