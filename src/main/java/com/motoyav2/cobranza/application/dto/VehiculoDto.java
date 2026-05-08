package com.motoyav2.cobranza.application.dto;

/**
 * Datos del vehículo financiado, extraídos de la factura del contrato.
 * Puede ser null si el contrato aún no tiene factura cargada.
 */
public record VehiculoDto(
        String marca,
        String modelo,
        Integer anio,
        String color,
        String serieMotor,
        String serieChasis,
        String numeroFactura,
        String fechaEmision
) {}
