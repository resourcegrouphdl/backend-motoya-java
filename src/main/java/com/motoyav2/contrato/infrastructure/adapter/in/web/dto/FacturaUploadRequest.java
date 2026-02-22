package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import lombok.Builder;

@Builder
public record FacturaUploadRequest(
    String numeroFactura,
    String urlDocumento,
    String nombreArchivo,
    String tipoArchivo,
    Integer tamanioBytes,
    String marcaVehiculo,
    String modeloVehiculo,
    Integer anioVehiculo,
    String colorVehiculo,
    String serieMotor,
    String serieChasis
    // estadoValidacion: 'PENDIENTE';
) {
}
