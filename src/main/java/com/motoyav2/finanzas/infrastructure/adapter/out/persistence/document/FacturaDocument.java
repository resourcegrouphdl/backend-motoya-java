package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacturaDocument {
    private String id;
    private String numero;
    private String tiendaId;
    private String tiendaNombre;
    private String ventaId;
    private String clienteNombre;
    private String motoModelo;
    // Datos completos del vehículo (de facturaVehiculo.estadoValidacion=APROBADO)
    private String marcaVehiculo;
    private String modeloVehiculo;
    private Integer anioVehiculo;
    private String colorVehiculo;
    private String serieMotor;
    private String serieChasis;
    private String urlDocumentoFactura;
    private String fechaEmisionFactura;
    private Double montoTotal;
    private String fechaFactura;
    private Integer condicionPago;
    private String estado;
    private String creadoEn;
    private String actualizadoEn;
    private String creadoPor;
    private Boolean alertaActiva;
    private Boolean tieneVencidos;
}
