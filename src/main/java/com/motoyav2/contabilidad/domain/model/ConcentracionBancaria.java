package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConcentracionBancaria {

    String banco;
    int cantidadOperaciones;
    Double montoTotal;
    Double porcentaje;
}
