package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VehiculoDto {

    private String id;
    private String marca;
    private String modelo;
    private String anio;
    private String color;
    private Integer cilindrada;
    private String descripcionCompleta;
    private Double precioReferencial;
}
