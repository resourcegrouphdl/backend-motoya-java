package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

public record ValidarDocumentoRequest(
        @NotNull Boolean aprobado,
        String observaciones
) {
}
