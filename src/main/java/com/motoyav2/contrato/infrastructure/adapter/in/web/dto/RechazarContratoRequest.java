package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazarContratoRequest(
        @NotBlank @Size(min = 10, message = "El motivo debe tener al menos 10 caracteres") String motivo
) {
}
