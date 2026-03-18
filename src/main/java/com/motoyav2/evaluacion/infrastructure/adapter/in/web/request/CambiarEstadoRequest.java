package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(
        @NotNull(message = "nuevoEstado es requerido")
        String nuevoEstado,
        @NotBlank(message = "usuarioId es requerido")
        String usuarioId,
        @NotBlank(message = "usuarioNombre es requerido")
        String usuarioNombre,
        String motivo
) {}
