package com.motoyav2.evaluacion.infrastructure.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;

public record VerificarReferenciaRequest(
        @NotBlank String estadoVerificacion,
        String resultadoContacto,
        Integer scoreVerificacion,
        String observaciones,
        String actitudDuranteContacto,
        String evaluadorId
) {}
