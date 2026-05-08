package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class DesgloseCuota {
    int numero;
    Instant fechaVencimiento;
    double montoTotal;
    double montoCapital;
    double montoInteres;
}
