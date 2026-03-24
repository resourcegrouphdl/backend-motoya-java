package com.motoyav2.financiamiento.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado completo de una simulación de crédito.
 */
@Value
@Builder
public class ResultadoSimulacion {

    // ── Eco de inputs ────────────────────────────────────────────────────────
    BigDecimal precioVehiculo;
    BigDecimal cuotaInicial;
    BigDecimal gastosAdministrativos;
    int        numeroCuotas;
    BigDecimal tea;

    // ── Calculados ───────────────────────────────────────────────────────────

    /** Monto financiado = precioVehiculo + gastosAdministrativos - cuotaInicial. */
    BigDecimal montoFinanciado;

    /** Tasa quincenal periódica derivada del TEA: i = (1+TEA)^(1/24) - 1. */
    BigDecimal tasaQuincenal;

    /**
     * Cuota fija por período (sistema francés / PMT).
     * cuota = P * i*(1+i)^n / ((1+i)^n - 1)
     */
    BigDecimal cuotaQuincenal;

    /** Suma de todos los intereses del cronograma. */
    BigDecimal totalIntereses;

    /**
     * Total desembolsado por el cliente:
     * cuotaInicial + suma de todas las cuotas quincenales.
     */
    BigDecimal totalPagar;

    /**
     * Costo Efectivo Anual (%), considerando que el cliente recibe
     * (precioVehiculo - cuotaInicial) pero financia el costo total.
     * Calculado por IRR (Newton-Raphson).
     */
    BigDecimal tcea;

    /** Cronograma de amortización completo. */
    List<CuotaCronograma> cronograma;
}
