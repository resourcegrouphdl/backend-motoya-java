package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * Cronograma con desglose capital/interés por contrato.
 * Calculado a partir de contratos + tasas de evaluación.
 * Persiste en contabilidad_cuotas/{contratoId}.
 */
@Value
@Builder
public class ContabilidadCuota {
    String contratoId;
    String tiendaId;
    int numeroCuotas;
    double montoFinanciar;
    double tasaInteres;
    double interesTotal;
    double capitalTotal;
    List<DesgloseCuota> cuotas;
    Instant calculadoEn;
}
