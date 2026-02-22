package com.motoyav2.contrato.domain.model;

import com.motoyav2.contrato.domain.enums.EstadoDePago;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
@Builder
public record CuotaCronograma(
    Integer numeroCuota,
    Instant fechaVencimiento,
    BigDecimal montoCuota,
    BigDecimal montoCapital,
    BigDecimal montoInteres,
    BigDecimal saldoPendiente,
    EstadoDePago estadoPago,
    Instant fechaPago,
    BigDecimal montoPagado,
    Integer diasMora,
    BigDecimal montoMora
) {
}
