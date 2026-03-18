package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Usuario {
    String id;
    String nombre;  // firstName + lastName
    String email;
    String rol;     // userType → admin | supervisor | asesor | evaluador | vendedor
}
