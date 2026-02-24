package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record NumeroDeTituloRequest(
        @NotBlank String numeroDeTitulo
) {
}
