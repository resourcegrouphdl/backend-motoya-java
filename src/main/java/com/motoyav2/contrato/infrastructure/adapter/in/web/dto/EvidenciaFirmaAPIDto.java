package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;

public record EvidenciaFirmaAPIDto(
    String id,
    String tipoEvidencia ,
    String urlEvidencia ,
    String nombreArchivo ,
    String tipoArchivo ,
    Integer tamanioBytes,
    String fechaSubida ,
    String subidoPor ,
    String descripcion ,
    EstadoValidacion estadoValidacion,
    // Campos de admin (solo lectura para tienda)
    String validadoPor,
    String fechaValidacion,
    String observacionesValidacion
) {
}
