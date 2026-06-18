package com.motoyav2.finanzas.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.domain.enums.MetodoPago;
import com.motoyav2.finanzas.domain.enums.TipoConceptoPago;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    String voucherGcsPath;
    MetodoPago metodoPago;
    // Document AI
    String documentAiStatus;
    Map<String, String> documentAiCampos;
    String documentAiProcesadoEn;
}
