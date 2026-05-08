package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class LineaIngreso {

    String id;
    String contratoId;
    Double monto;
    LocalDate fecha;
    String tipo;
    String voucherId;
}
