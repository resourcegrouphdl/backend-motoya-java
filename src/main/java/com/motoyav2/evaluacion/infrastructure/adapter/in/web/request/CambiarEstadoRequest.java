package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(
        @NotNull(message = "nuevoEstado es requerido")
        String nuevoEstado,
        String usuarioId,
        String usuarioNombre,
        String motivo
) {}
