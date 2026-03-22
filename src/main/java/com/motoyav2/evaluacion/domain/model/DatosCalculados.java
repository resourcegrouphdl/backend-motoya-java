package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class DatosCalculados {
    BigDecimal precioMoto;
    BigDecimal adicionalFijo;
    BigDecimal precioTotal;
    BigDecimal inicialMinima;
    BigDecimal inicialFinal;
    BigDecimal montoFinanciar;
    boolean montoAjustadoPorTope;
    BigDecimal sumaTotal;
}
