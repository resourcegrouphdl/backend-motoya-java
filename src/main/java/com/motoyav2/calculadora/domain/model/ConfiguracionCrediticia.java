package com.motoyav2.calculadora.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Parámetros crediticios configurables por el administrador.
 * Se persisten en Firestore (documento "default" en colección configuracion_crediticia).
 * Refleja los requisitos de transparencia de la SBS Perú.
 */
@Value
@Builder(toBuilder = true)
public class ConfiguracionCrediticia {

    String id;

    /** Gastos administrativos fijos en soles (ej: 890.00) */
    BigDecimal gastosAdministrativos;

    /** Porcentaje mínimo de inicial sobre precio total (ej: 0.20 = 20%) */
    BigDecimal porcentajeInicialMinima;

    /** Tope máximo del monto a financiar en soles (ej: 5400.00) */
    BigDecimal montoMaximoFinanciar;

    /** Monto mínimo a financiar en soles (ej: 500.00) */
    BigDecimal montoMinimoFinanciar;

    /**
     * Tasa de seguro de desgravamen mensual sobre el saldo vigente.
     * Ejemplo: 0.0004 = 0.04% mensual (regulado por SBS, Ley 26702).
     */
    BigDecimal tasaSeguroDesgravamenMensual;

    /** Comisión de desembolso en soles (cargo único al inicio) */
    BigDecimal comisionDesembolso;

    /** TEA por defecto si el plazo no tiene configuración específica (ej: 0.72 = 72%) */
    BigDecimal teaDefault;

    /** Configuración de TEA por plazo en meses */
    List<PlazoTeaConfig> plazos;

    Instant actualizadoEn;
    String actualizadoPor;
}