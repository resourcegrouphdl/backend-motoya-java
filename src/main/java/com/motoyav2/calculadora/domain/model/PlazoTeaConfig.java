package com.motoyav2.calculadora.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Configuración de TEA para un plazo específico en meses.
 */
@Value
@Builder
public class PlazoTeaConfig {

    /** Plazo en meses */
    int meses;

    /** TEA como ratio (ej: 0.65 = 65% anual) */
    BigDecimal tea;

    /** Etiqueta descriptiva (ej: "Recomendado", "Cuota menor", "Pago rápido") */
    String etiqueta;
}