package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EvaluacionEntrevistaDto {

    private Integer actitud;
    private Integer disposicion;
    private Integer claridad;
    private Integer estabilidadLaboral;
    private Integer estabilidadDomiciliaria;
    private Integer historicoCrediticio;
    private Boolean ingresoVerificable;
    private Boolean comprobanteIngresos;
    private Double gastosMensuales;
    private Double capacidadPagoCalculada;
    private Integer scoreTotal;
    private String observaciones;
    private String recomendacion;
    private String fechaEntrevista;
    private Integer duracion;
    private String realizadoPor;
}
