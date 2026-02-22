package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import lombok.Builder;

@Builder
public record UploadBoucherResponse(
    String message,
    String boucherId
) {
}
