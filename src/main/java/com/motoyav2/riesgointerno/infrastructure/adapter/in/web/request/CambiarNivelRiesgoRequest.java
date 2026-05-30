package com.motoyav2.riesgointerno.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CambiarNivelRiesgoRequest {
    @NotBlank
    private String nuevoNivel;
    private String motivo;
}
