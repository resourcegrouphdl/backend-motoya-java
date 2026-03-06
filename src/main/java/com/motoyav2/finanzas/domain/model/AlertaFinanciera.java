package com.motoyav2.finanzas.domain.model;

import com.motoyav2.finanzas.domain.enums.ModuloAlerta;
import com.motoyav2.finanzas.domain.enums.TipoAlerta;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class AlertaFinanciera {
    String id;
    TipoAlerta tipo;
    String mensaje;
    BigDecimal monto;
    String referencia;
    ModuloAlerta modulo;
    String ruta;
}
