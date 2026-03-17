package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expedientecompleto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AsesorDto {
    private final String id;
    private final String nombre;
    private final String rol;     // admin | supervisor | asesor | evaluador | vendedor
    private final String email;
}
