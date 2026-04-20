package com.motoyav2.gestion.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CambiarEstadoRequest(
        @NotBlank String estado
) {}
