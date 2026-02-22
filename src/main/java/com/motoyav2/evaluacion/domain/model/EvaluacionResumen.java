package com.motoyav2.evaluacion.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EvaluacionResumen {

    private String id;
    private String numeroEvaluacion;
    private String codigoSolicitud;
    private String estado;
    private String etapa;
    private String prioridad;
    private Integer progreso;
    private String scoreFinal;

    // titular
    private String nombreTitular;
    private String documentoTitular;
    private String telefonoTitular;

    // vehiculo
    private String vehiculoDescripcion;

    // financiamiento
    private BigDecimal montoVehiculo;
    private BigDecimal montoFinanciar;

    // asignacion
    private String asignadoA;
    private String nombreEvaluador;

    // tienda
    private String tiendaId;
    private String tiendaNombre;

    // indicadores
    private int alertasCount;
    private boolean tieneFiador;

    // auditoria
    private String creadoEn;
    private String actualizadoEn;
}