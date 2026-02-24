package com.motoyav2.contrato.domain.model;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.enums.TipoEvidencia;
import lombok.Builder;

import java.time.Instant;

@Builder
public record EvidenciaFirma(
    String id,
    TipoEvidencia tipoEvidencia,
    String urlEvidencia,
    String nombreArchivo,
    String tipoArchivo,
    Integer tamanioBytes,
    Instant fechaSubida,
    String subidoPor,
    String descripcion,
    // Validación admin
    EstadoValidacion estadoValidacion,
    String observacionesValidacion,
    String validadoPor,
    Instant fechaValidacion
) {
}
