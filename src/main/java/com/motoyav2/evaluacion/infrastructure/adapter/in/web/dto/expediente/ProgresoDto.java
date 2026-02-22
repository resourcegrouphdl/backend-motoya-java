package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProgresoDto {

    private Integer porcentaje;
    private Integer etapasCompletadas;
    private Integer etapasTotales;
    private String descripcion;
}
