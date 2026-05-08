package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PuntoRecaudacion {

    String etiqueta;
    int cantidadPagos;
    Double montoTotal;
}
