package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EtapaDto {

    private Integer numero;
    private String nombre;
    private String descripcion;
    private String estado;
    private String fechaInicio;
    private String fechaFin;
    private String completadoPor;
    private String observaciones;
}
