package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DatosVendedor {
    String id;
    String nombre;
    String tienda;
    String email;
    String telefono;
}
