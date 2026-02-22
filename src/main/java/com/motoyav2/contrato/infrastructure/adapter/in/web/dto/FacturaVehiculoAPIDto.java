package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import lombok.Builder;

@Builder
public record FacturaVehiculoAPIDto(
    String id,
    String numeroFactura,
    String urlDocumento,
    String nombreArchivo,
    String tipoArchivo,
    Integer tamanioBytes,
    String fechaEmision,
    String fechaSubida,
    String marcaVehiculo,
    String modeloVehiculo,
    Integer anioVehiculo,
    String colorVehiculo,
    String serieMotor,
    String serieChasis,
    EstadoValidacion estadoValidacion,
    // Campos de admin (solo lectura para tienda)
    String validadoPor,
    String fechaValidacion,
    String observacionesValidacion
) {
}
