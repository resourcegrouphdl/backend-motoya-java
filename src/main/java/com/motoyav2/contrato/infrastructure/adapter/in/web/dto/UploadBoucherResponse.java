package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record UploadBoucherResponse(
    String message,
    String boucherId,
    List<BoucherPagoInicialAPIDto> bouchers
) {
}
