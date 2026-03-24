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

    // ── Datos de precio ───────────────────────────────────────────────────────
    BigDecimal precioVehiculo;
    BigDecimal gastosAdministrativos;
    BigDecimal precioTotal;

    // ── Datos de financiamiento ───────────────────────────────────────────────
    BigDecimal inicialMinima;
    BigDecimal inicialAplicada;
    BigDecimal montoFinanciar;
    int plazoMeses;
    BigDecimal comisionDesembolso;

    // ── Tasas (SBS obliga divulgar TEA y TCEA) ───────────────────────────────
    /** Tasa Efectiva Anual (ratio, ej: 0.72 = 72%) */
    BigDecimal tea;
    /** Tasa Efectiva Mensual derivada de TEA */
    BigDecimal tem;
    /**
     * Tasa de Costo Efectivo Anual: incluye intereses + seguro + comisiones.
     * Calculada por método TIR (Newton-Raphson).
     */
    BigDecimal tcea;

    // ── Cuotas ────────────────────────────────────────────────────────────────
    /** Cuota fija de capital + interés (sin seguro) */
    BigDecimal cuotaBaseMensual;
    /** Cuota promedio total (incluyendo seguro en caso de saldo decreciente) */
    BigDecimal cuotaTotalMensual;

    // ── Totales ───────────────────────────────────────────────────────────────
    BigDecimal totalIntereses;
    BigDecimal totalSeguro;
    BigDecimal totalAPagar;

    // ── Cronograma ────────────────────────────────────────────────────────────
    List<CuotaCronograma> cronograma;

    // ── Metadata ─────────────────────────────────────────────────────────────
    boolean inicialAjustadaPorTope;
    String advertencia;
}