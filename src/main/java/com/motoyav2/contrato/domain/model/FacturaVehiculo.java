package com.motoyav2.contrato.domain.model;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import lombok.Builder;

import java.time.Instant;

@Builder
public record FacturaVehiculo(
    String id,
    String numeroFactura,
    String urlDocumento,
    String nombreArchivo,
    String tipoArchivo,
    Integer tamanioBytes,
    Instant fechaEmision,
    Instant fechaSubida,
    String marcaVehiculo,
    String modeloVehiculo,
    Integer anioVehiculo,
    String colorVehiculo,
    String serieMotor,
    String serieChasis,
    EstadoValidacion estadoValidacion,
    String observacionesValidacion,
    String validadoPor,
    Instant fechaValidacion
) {
}
