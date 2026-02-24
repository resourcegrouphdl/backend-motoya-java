package com.motoyav2.contrato.domain.model;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import lombok.Builder;

import java.time.Instant;

@Builder
public record EvidenciaDocumento(
        String id,
        String tipoEvidencia,
        String urlEvidencia,
        String nombreArchivo,
        String tipoArchivo,
        Long tamanioBytes,
        Instant fechaSubida,
        String descripcion,
        EstadoValidacion estadoValidacion,
        String validadoPor,
        Instant fechaValidacion,
        String observacionesValidacion
) {
}