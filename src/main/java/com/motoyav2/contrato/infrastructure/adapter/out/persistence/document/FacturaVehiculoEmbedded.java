package com.motoyav2.contrato.infrastructure.adapter.out.persistence.document;

import com.google.cloud.Timestamp;
import lombok.Data;

@Data
public class FacturaVehiculoEmbedded {
    private String id;
    private String numeroFactura;
    private String urlDocumento;
    private String nombreArchivo;
    private String tipoArchivo;
    private Integer tamanioBytes;
    private Timestamp fechaEmision;
    private Timestamp fechaSubida;
    private String marcaVehiculo;
    private String modeloVehiculo;
    private Integer anioVehiculo;
    private String colorVehiculo;
    private String serieMotor;
    private String serieChasis;
    private String estadoValidacion;
    private String observacionesValidacion;
    private String validadoPor;
    private Timestamp fechaValidacion;
}
