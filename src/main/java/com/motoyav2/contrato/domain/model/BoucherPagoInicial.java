package com.motoyav2.contrato.domain.model;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import lombok.Builder;

import java.time.Instant;

@Builder
public record BoucherPagoInicial(
    String id,
    String urlDocumento,
    String nombreArchivo,
    String tipoArchivo,
    Integer tamanioBytes,
    Instant fechaSubida,
    EstadoValidacion estadoValidacion,
    String observacionesValidacion,
    String validadoPor,
    Instant fechaValidacion
) {

}