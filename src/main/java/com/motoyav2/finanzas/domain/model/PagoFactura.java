package com.motoyav2.finanzas.domain.model;

import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.domain.enums.MetodoPago;
import com.motoyav2.finanzas.domain.enums.TipoConceptoPago;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class PagoFactura {
    String id;
    String facturaId;
    int numero;
    TipoConceptoPago concepto;
    BigDecimal monto;
    LocalDate fechaProgramada;
    LocalDate fechaPago;
    EstadoPago estado;
    String voucherUrl;
    MetodoPago metodoPago;
}
