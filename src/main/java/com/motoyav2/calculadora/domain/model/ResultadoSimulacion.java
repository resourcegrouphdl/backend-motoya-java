package com.motoyav2.calculadora.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado completo de una simulación crediticia.
 * Incluye todos los indicadores requeridos por la SBS (TEA, TCEA, cronograma).
 */
@Value
@Builder
public class ResultadoSimulacion {

    // ── Frecuencia ────────────────────────────────────────────────────────────
    FrequenciaPago frecuencia;
    int            numeroCuotas;

    // ── Capital ───────────────────────────────────────────────────────────────
    BigDecimal precioVehiculo;
    BigDecimal soat;
    BigDecimal gastosAdministrativos;
    /** precioVehiculo + soat + gastosAdmin (antes de comisión y de la inicial) */
    BigDecimal capitalBase;

    // ── Comisión ──────────────────────────────────────────────────────────────
    /** Monto de comisión calculado (S/) */
    BigDecimal comisionMonto;
    /** true → incluida en el capital; false → cobrada al desembolso */
    boolean    comisionFinanciada;

    // ── Financiamiento ────────────────────────────────────────────────────────
    BigDecimal inicialMinima;
    BigDecimal inicialAplicada;
    /** Capital efectivamente financiado (capitalBase + comisión si financiada − inicial) */
    BigDecimal montoFinanciar;
    /**
     * Efectivo neto recibido por el cliente.
     * = montoFinanciar − comisionMonto (si no financiada).
     * Usado como PV0 en el cálculo de TCEA (transparencia SBS).
     */
    BigDecimal efectivoNeto;

    // ── Tasas (SBS obliga divulgar TEA y TCEA) ────────────────────────────────
    /** Tasa Efectiva Anual aplicada (ratio, ej: 0.72 = 72%) */
    BigDecimal tea;
    /** Tasa periódica derivada de TEA (semanal o mensual según frecuencia) */
    BigDecimal tasaPeriodica;
    /**
     * Tasa de Costo Efectivo Anual.
     * Calculada por TIR Newton-Raphson sobre flujos reales (cuotas + seguro)
     * descontados desde el efectivoNeto.
     */
    BigDecimal tcea;

    // ── Cuotas ────────────────────────────────────────────────────────────────
    /** Cuota fija de capital + interés (sin seguro) */
    BigDecimal cuotaBase;
    /** Cuota promedio total (capital + interés + seguro decreciente) */
    BigDecimal cuotaTotalPromedio;

    // ── Totales ───────────────────────────────────────────────────────────────
    BigDecimal totalIntereses;
    BigDecimal totalSeguro;
    BigDecimal totalAPagar;

    // ── Cronograma ────────────────────────────────────────────────────────────
    List<CuotaCronograma> cronograma;

    // ── Metadata ──────────────────────────────────────────────────────────────
    boolean inicialAjustadaPorTope;
    String  advertencia;
}