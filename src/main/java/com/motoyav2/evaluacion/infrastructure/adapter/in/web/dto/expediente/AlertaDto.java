package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertaDto {

    private String tipo;
    private String mensaje;
    private String descripcion;
    private String fechaCreacion;
    private Boolean resuelta;
    private String fechaResolucion;
}
