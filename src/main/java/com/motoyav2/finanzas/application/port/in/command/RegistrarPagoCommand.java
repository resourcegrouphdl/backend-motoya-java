package com.motoyav2.finanzas.application.port.in.command;

import com.motoyav2.finanzas.domain.enums.MetodoPago;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
public class RegistrarPagoCommand {
    String facturaId;
    String pagoId;
    BigDecimal monto;
    LocalDate fechaPago;
    MetodoPago metodoPago;
}
