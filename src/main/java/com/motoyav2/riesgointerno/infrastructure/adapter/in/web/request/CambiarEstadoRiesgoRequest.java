package com.motoyav2.riesgointerno.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CambiarEstadoRiesgoRequest {
    @NotBlank
    private String nuevoEstado;
    private String motivo;
}
