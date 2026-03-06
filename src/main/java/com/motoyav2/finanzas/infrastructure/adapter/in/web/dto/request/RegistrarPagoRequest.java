package com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request;

import com.motoyav2.finanzas.domain.enums.MetodoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RegistrarPagoRequest {
    @NotBlank String facturaId;
    @NotBlank String pagoId;
    @NotNull @DecimalMin("0.01") BigDecimal monto;
    @NotNull LocalDate fechaPago;
    @NotNull MetodoPago metodoPago;
}
