package com.motoyav2.finanzas.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.motoyav2.finanzas.domain.enums.EstadoCuenta;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CuotaCuenta {
    String id;
    String cuentaId;
    int numero;
    BigDecimal monto;
    LocalDate fechaVencimiento;
    LocalDate fechaPago;
    EstadoCuenta estado;
    // Voucher
    String voucherUrl;
    // Document AI
    String documentAiStatus;
    Map<String, String> documentAiCampos;
    String documentAiProcesadoEn;
}
