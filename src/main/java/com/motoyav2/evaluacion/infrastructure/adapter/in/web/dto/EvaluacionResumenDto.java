package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class EvaluacionResumenDto {

    private String id;
    private String numeroEvaluacion;
    private String codigoSolicitud;
    private String estado;
    private String etapa;
    private String prioridad;
    private Integer progreso;
    private String scoreFinal;

    private String nombreTitular;
    private String documentoTitular;
    private String telefonoTitular;

    private String vehiculoDescripcion;
    private BigDecimal montoVehiculo;
    private BigDecimal montoFinanciar;

    private String asignadoA;
    private String nombreEvaluador;

    private String tiendaId;
    private String tiendaNombre;

    private int alertasCount;
    private boolean tieneFiador;

    private String creadoEn;
    private String actualizadoEn;
}
