package com.motoyav2.calculadora.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Configuración de TEA para un plazo específico.
 *
 * 'periodos' representa el número de cuotas según la frecuencia de pago:
 *   - MONTHLY: número de meses
 *   - WEEKLY:  número de semanas
 */
@Value
@Builder
public class PlazoTeaConfig {

    /** Número de cuotas (semanas o meses según frecuencia) */
    int periodos;

    FrequenciaPago frecuencia;

    /** TEA como ratio (ej: 0.65 = 65% anual) */
    BigDecimal tea;

    /** Etiqueta descriptiva (ej: "Recomendado", "Cuota menor") */
    String etiqueta;
}