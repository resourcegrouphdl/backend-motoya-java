package com.motoyav2.finanzas.application.port.in.command;

import com.motoyav2.finanzas.domain.enums.TipoCuenta;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
public class CrearCuentaCommand {
    TipoCuenta tipo;
    String proveedor;
    String descripcion;
    String numeroDocumento;
    BigDecimal montoTotal;
    int numeroCuotas;
    LocalDate fechaVencimiento;
    String creadoPor;
}
