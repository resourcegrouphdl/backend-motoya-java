package com.motoyav2.contrato.domain.model;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;

import java.time.Instant;

public record DocumentoAdjunto(
        String url,
        String nombreArchivo,
        EstadoValidacion estadoValidacion,
        String observacion,
        String validadoPor,
        Instant fechaValidacion
) {
}
