package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class OpcionFinanciamiento {
    int plazo;               // Meses
    int quincenas;
    BigDecimal tasa;
    BigDecimal tasaPorcentaje;
    BigDecimal interesTotal;
    BigDecimal montoTotalPagar;
    BigDecimal sumaTotal;
    BigDecimal cuotaQuincenal;
    BigDecimal cuotaMensual;
    BigDecimal tea;
    String recomendacion;
    String popularidad;      // "popular" | "economico" | "rapido"
}
