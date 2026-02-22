package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import lombok.Builder;

@Builder
public record BoucherPagoInicialAPIDto(

    String id,
    String urlDocumento,
    String nombreArchivo,
    String tipoArchivo,
    String tamanioBytes,
    String fechaSubida,
    EstadoValidacion estadoValidacion,
    // Campos de admin (solo lectura para tienda)
    String validadoPor,
    String fechaValidacion,
    String observacionesValidacion

    ) {
}
