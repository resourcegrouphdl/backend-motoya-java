package com.motoyav2.evaluacion.application.dto;

import com.google.cloud.Timestamp;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SolicitudResumenDto {
    String id;
    String numeroSolicitud;
    String codigoDeSolicitud;
    String estado;
    String prioridad;
    String titularNombre;
    String titularDocumento;
    String titularTelefono;
    String vehiculoDescripcion;
    Double precioVehiculo;
    Double scoreFinal;
    Double scoreDocumental;
    String asesorAsignadoId;
    String vendedorNombre;
    String vendedorTienda;
    Boolean certificadoGenerado;
    String urlCertificado;
    Timestamp createdAt;
    Timestamp updatedAt;
}
