package com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request;

import com.motoyav2.finanzas.domain.enums.TipoCuenta;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CrearCuentaRequest {
    @NotNull TipoCuenta tipo;
    @NotBlank @Size(max = 150) String proveedor;
    @NotBlank @Size(max = 250) String descripcion;
    @NotBlank @Size(max = 50)  String numeroDocumento;
    @NotNull @DecimalMin("0.01") BigDecimal montoTotal;
    @Min(1) @Max(36) int numeroCuotas;
    @NotNull LocalDate fechaVencimiento;
}
