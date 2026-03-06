package com.motoyav2.finanzas.domain.model;

import com.motoyav2.finanzas.domain.enums.EstadoCuenta;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class CuotaCuenta {
    String id;
    String cuentaId;
    int numero;
    BigDecimal monto;
    LocalDate fechaVencimiento;
    LocalDate fechaPago;
    EstadoCuenta estado;
}
