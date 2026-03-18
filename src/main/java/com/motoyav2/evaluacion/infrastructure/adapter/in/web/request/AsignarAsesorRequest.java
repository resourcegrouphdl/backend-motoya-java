package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;

public record AsignarAsesorRequest(
        @NotBlank String asesorId,
        @NotBlank String asesorNombre,
        String asesorEmail
) {}
