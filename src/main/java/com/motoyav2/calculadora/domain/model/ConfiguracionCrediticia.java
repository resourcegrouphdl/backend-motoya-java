package com.motoyav2.calculadora.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Parámetros crediticios configurables por el administrador.
 * Persisten en Firestore: colección 'configuracion_crediticia' / documento 'default'.
 * Refleja los requisitos de transparencia SBS Perú.
 */
@Value
@Builder(toBuilder = true)
public class ConfiguracionCrediticia {

    String id;

    /** Gastos administrativos fijos en soles (ej: 890.00) — siempre financiables */
    BigDecimal gastosAdministrativos;

    /** Porcentaje mínimo de inicial sobre el precio total (ej: 0.20 = 20%) */
    BigDecimal porcentajeInicialMinima;

    /** Tope máximo del monto a financiar en soles */
    BigDecimal montoMaximoFinanciar;

    /** Monto mínimo a financiar en soles */
    BigDecimal montoMinimoFinanciar;

    /**
     * Tasa de seguro de desgravamen mensual sobre el saldo vigente.
     * Ejemplo: 0.0004 = 0.04% mensual (Ley 26702 SBS).
     * Se aplica sobre saldo en cada período (independientemente de la frecuencia).
     */
    BigDecimal tasaSeguroDesgravamenMensual;

    /**
     * Comisión por defecto. Se usa si la simulación no envía una comisión explícita.
     * null → sin comisión.
     */
    ComisionConfig comisionDefault;

    /** TEA por defecto si el plazo no tiene configuración específica */
    BigDecimal teaDefault;

    /** Plazos disponibles con TEA por plazo y frecuencia */
    List<PlazoTeaConfig> plazos;

    Instant actualizadoEn;
    String  actualizadoPor;
}