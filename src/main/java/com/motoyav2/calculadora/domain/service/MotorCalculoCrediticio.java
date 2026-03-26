package com.motoyav2.calculadora.domain.service;

import com.motoyav2.calculadora.domain.model.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de cálculo crediticio — Sistema Francés (cuota fija).
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * FRECUENCIAS SOPORTADAS
 *   MONTHLY → 1 cuota por mes
 *   WEEKLY  → 1 cuota cada 7 días
 *
 * TASAS
 *   MONTHLY: tasa_periódica = (1 + TEA)^(1/12) − 1
 *   WEEKLY:  tasa_periódica = (1 + TEA)^(7/360) − 1
 *   (capitalización compuesta — NO división simple)
 *
 * SISTEMA FRANCÉS
 *   cuota_base = PV × i / (1 − (1+i)^−n)
 *   interés_k  = saldo_{k−1} × i
 *   amort_k    = cuota_base − interés_k
 *   seguro_k   = saldo_{k−1} × tasa_seguro_mensual   (proporcional para WEEKLY)
 *
 * COMISIÓN
 *   financiada  = true  → suma al capital → cuotas más grandes
 *   financiada  = false → se descuenta del desembolso → afecta TCEA
 *
 * TCEA (SBS Circular B-2172-2002)
 *   TIR de los flujos {cuotaTotal_k} descontados desde efectivoNeto (PV0).
 *   TCEA_MONTHLY = (1 + r_mensual)^12 − 1
 *   TCEA_WEEKLY  = (1 + r_semanal)^(360/7) − 1
 *
 * Normativa aplicable:
 *   - Resolución SBS N° 11356-2008: TEA y TCEA obligatorios
 *   - Ley 26702: seguro de desgravamen
 *   - Circular SBS B-2216-2010: cronograma obligatorio
 * ═══════════════════════════════════════════════════════════════════════════════
 */
public final class MotorCalculoCrediticio {

    private static final MathContext MC    = MathContext.DECIMAL128;
    private static final int         SCALE = 6;

    /** Año de 360 días (convención financiera peruana) */
    private static final double DIAS_ANO    = 360.0;
    private static final double DIAS_SEMANA = 7.0;

    private MotorCalculoCrediticio() {}

    // =========================================================================
    // API pública
    // =========================================================================

    public static ResultadoSimulacion calcular(
            ParametrosSimulacion params,
            ConfiguracionCrediticia config) {

        FrequenciaPago frecuencia  = params.getFrecuencia();
        BigDecimal     precioV     = params.getPrecioVehiculo();
        BigDecimal     soat        = params.getSoat() != null ? params.getSoat() : BigDecimal.ZERO;
        BigDecimal     gastosAdmin = config.getGastosAdministrativos();

        // ── Capital base (costos financiables fijos) ─────────────────────────
        BigDecimal capitalBase = precioV.add(soat).add(gastosAdmin);

        // ── Comisión ─────────────────────────────────────────────────────────
        ComisionConfig comisionCfg = params.getComision() != null
                ? params.getComision()
                : config.getComisionDefault();

        BigDecimal comisionMonto  = BigDecimal.ZERO;
        boolean    comisionFinanciada = false;
        if (comisionCfg != null && comisionCfg.getValor() != null
                && comisionCfg.getValor().compareTo(BigDecimal.ZERO) > 0) {
            comisionFinanciada = comisionCfg.isFinanciada();
            if (comisionCfg.getTipo() == TipoComision.FIXED) {
                comisionMonto = comisionCfg.getValor().setScale(2, RoundingMode.HALF_UP);
            } else { // PERCENTAGE
                comisionMonto = capitalBase.multiply(comisionCfg.getValor(), MC)
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        // ── Capital a financiar antes de inicial ──────────────────────────────
        // Si la comisión es financiada, se incorpora al capital.
        BigDecimal capitalTotal = comisionFinanciada
                ? capitalBase.add(comisionMonto)
                : capitalBase;

        // ── Inicial mínima ────────────────────────────────────────────────────
        BigDecimal inicialMinimaPct = capitalTotal.multiply(config.getPorcentajeInicialMinima(), MC);
        BigDecimal montoConMinima   = capitalTotal.subtract(inicialMinimaPct);

        BigDecimal inicialMinima;
        if (montoConMinima.compareTo(config.getMontoMaximoFinanciar()) > 0) {
            inicialMinima = capitalTotal.subtract(config.getMontoMaximoFinanciar())
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            inicialMinima = inicialMinimaPct.setScale(2, RoundingMode.HALF_UP);
        }

        // ── Inicial aplicada ──────────────────────────────────────────────────
        BigDecimal inicialAplicada = (params.getInicial() != null
                && params.getInicial().compareTo(inicialMinima) >= 0)
                ? params.getInicial().setScale(2, RoundingMode.HALF_UP)
                : inicialMinima;

        BigDecimal montoFinanciar = capitalTotal.subtract(inicialAplicada)
                .setScale(2, RoundingMode.HALF_UP);
        boolean ajustado = false;

        if (montoFinanciar.compareTo(config.getMontoMaximoFinanciar()) > 0) {
            montoFinanciar  = config.getMontoMaximoFinanciar();
            inicialAplicada = capitalTotal.subtract(montoFinanciar).setScale(2, RoundingMode.HALF_UP);
            ajustado        = true;
        }

        // ── Efectivo neto (usado en TCEA) ──────────────────────────────────────
        // Si la comisión NO está financiada, se descuenta del efectivo recibido.
        BigDecimal efectivoNeto = comisionFinanciada
                ? montoFinanciar
                : montoFinanciar.subtract(comisionMonto).max(BigDecimal.ZERO);

        // ── TEA y tasa periódica ──────────────────────────────────────────────
        BigDecimal tea = params.getTeaOverride() != null
                ? params.getTeaOverride()
                : resolverTea(params.getNumeroCuotas(), frecuencia, config);

        double teaD      = tea.doubleValue();
        double tasaD     = tasaPeriodica(teaD, frecuencia);
        BigDecimal tasa  = BigDecimal.valueOf(tasaD).setScale(SCALE, RoundingMode.HALF_UP);

        // ── Tasa de seguro por período ────────────────────────────────────────
        // El seguro es mensual sobre saldo. Para WEEKLY se convierte al equivalente
        // de 7 días: seguro_k = saldo × [(1 + tasa_mensual)^(7/30) − 1]
        BigDecimal tasaSeguroMensual = params.isIncluirSeguro()
                ? config.getTasaSeguroDesgravamenMensual()
                : BigDecimal.ZERO;

        double tasaSeguroD = tasaSeguroMensual.doubleValue();
        double tasaSeguroPeriodoD;
        if (frecuencia == FrequenciaPago.WEEKLY) {
            // Proporcional diario × 7 (aproximación lineal SBS válida para tasas bajas)
            tasaSeguroPeriodoD = tasaSeguroD * (DIAS_SEMANA / 30.0);
        } else {
            tasaSeguroPeriodoD = tasaSeguroD;
        }
        BigDecimal tasaSeguroPeriodo = BigDecimal.valueOf(tasaSeguroPeriodoD)
                .setScale(SCALE + 2, RoundingMode.HALF_UP);

        int        n  = params.getNumeroCuotas();
        BigDecimal pv = montoFinanciar;

        // ── Cuota base (Sistema Francés) ──────────────────────────────────────
        BigDecimal cuotaBase;
        if (tasaD == 0.0) {
            cuotaBase = pv.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        } else {
            double pvD    = pv.doubleValue();
            double cuotaD = pvD * tasaD / (1.0 - Math.pow(1.0 + tasaD, -n));
            cuotaBase     = BigDecimal.valueOf(cuotaD).setScale(2, RoundingMode.HALF_UP);
        }

        // ── Cronograma de pagos ───────────────────────────────────────────────
        List<CuotaCronograma> cronograma      = new ArrayList<>(n);
        BigDecimal            saldo           = pv;
        BigDecimal            totalIntereses  = BigDecimal.ZERO;
        BigDecimal            totalSeguroAcum = BigDecimal.ZERO;
        BigDecimal            totalCuotasAcum = BigDecimal.ZERO;

        LocalDate fechaBase = fechaInicio(frecuencia);

        for (int i = 1; i <= n; i++) {
            BigDecimal saldoInicial = saldo.setScale(2, RoundingMode.HALF_UP);
            BigDecimal interesPer   = saldoInicial.multiply(tasa, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal amortizacion;
            BigDecimal cuotaFila;
            BigDecimal saldoFinal;

            if (i == n) {
                // Último período: salda exactamente el saldo pendiente
                amortizacion = saldoInicial;
                cuotaFila    = interesPer.add(amortizacion);
                saldoFinal   = BigDecimal.ZERO;
            } else {
                amortizacion = cuotaBase.subtract(interesPer).setScale(2, RoundingMode.HALF_UP);
                cuotaFila    = cuotaBase;
                saldoFinal   = saldoInicial.subtract(amortizacion).setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal seguroPer  = saldoInicial.multiply(tasaSeguroPeriodo, MC)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal cuotaTotal = cuotaFila.add(seguroPer);

            cronograma.add(CuotaCronograma.builder()
                    .numeroCuota(i)
                    .fechaVencimiento(fechaVencimiento(fechaBase, frecuencia, i))
                    .saldoInicial(saldoInicial)
                    .interes(interesPer)
                    .amortizacion(amortizacion)
                    .seguroDesgravamen(seguroPer)
                    .cuotaTotal(cuotaTotal)
                    .saldoFinal(saldoFinal)
                    .build());

            saldo           = saldoFinal;
            totalIntereses  = totalIntereses.add(interesPer);
            totalSeguroAcum = totalSeguroAcum.add(seguroPer);
            totalCuotasAcum = totalCuotasAcum.add(cuotaTotal);
        }

        // ── Totales ───────────────────────────────────────────────────────────
        // Si la comisión NO está financiada se suma al total que el cliente desembolsa.
        BigDecimal comisionNoFinanciada = comisionFinanciada ? BigDecimal.ZERO : comisionMonto;
        BigDecimal totalAPagar = inicialAplicada
                .add(totalCuotasAcum)
                .add(comisionNoFinanciada)
                .setScale(2, RoundingMode.HALF_UP);

        // ── TCEA ──────────────────────────────────────────────────────────────
        BigDecimal tcea = calcularTcea(efectivoNeto, cronograma, frecuencia);

        BigDecimal cuotaTotalPromedio = totalCuotasAcum
                .divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);

        return ResultadoSimulacion.builder()
                .frecuencia(frecuencia)
                .numeroCuotas(n)
                .precioVehiculo(precioV.setScale(2, RoundingMode.HALF_UP))
                .soat(soat.setScale(2, RoundingMode.HALF_UP))
                .gastosAdministrativos(gastosAdmin.setScale(2, RoundingMode.HALF_UP))
                .capitalBase(capitalBase.setScale(2, RoundingMode.HALF_UP))
                .comisionMonto(comisionMonto)
                .comisionFinanciada(comisionFinanciada)
                .inicialMinima(inicialMinima)
                .inicialAplicada(inicialAplicada)
                .montoFinanciar(pv)
                .efectivoNeto(efectivoNeto.setScale(2, RoundingMode.HALF_UP))
                .tea(tea)
                .tasaPeriodica(tasa)
                .tcea(tcea)
                .cuotaBase(cuotaBase)
                .cuotaTotalPromedio(cuotaTotalPromedio)
                .totalIntereses(totalIntereses.setScale(2, RoundingMode.HALF_UP))
                .totalSeguro(totalSeguroAcum.setScale(2, RoundingMode.HALF_UP))
                .totalAPagar(totalAPagar)
                .cronograma(cronograma)
                .inicialAjustadaPorTope(ajustado)
                .advertencia(ajustado
                        ? "Monto financiado ajustado al tope máximo de S/ " + config.getMontoMaximoFinanciar()
                        : null)
                .build();
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Convierte la TEA a tasa periódica usando capitalización compuesta.
     *
     * MONTHLY: i = (1 + TEA)^(1/12) − 1
     * WEEKLY:  i = (1 + TEA)^(7/360) − 1
     */
    static double tasaPeriodica(double tea, FrequenciaPago freq) {
        return switch (freq) {
            case MONTHLY -> Math.pow(1.0 + tea, 1.0 / 12.0) - 1.0;
            case WEEKLY  -> Math.pow(1.0 + tea, DIAS_SEMANA / DIAS_ANO) - 1.0;
        };
    }

    /**
     * Períodos por año para anualizar la TIR.
     * Usa el mismo año de 360 días de la conversión de tasa.
     */
    private static double periodosAnio(FrequenciaPago freq) {
        return switch (freq) {
            case MONTHLY -> 12.0;
            case WEEKLY  -> DIAS_ANO / DIAS_SEMANA;   // ≈ 51.4286
        };
    }

    /** Fecha del primer período */
    private static LocalDate fechaInicio(FrequenciaPago freq) {
        LocalDate hoy = LocalDate.now();
        return switch (freq) {
            case MONTHLY -> hoy.plusMonths(1).withDayOfMonth(1);
            case WEEKLY  -> hoy.plusWeeks(1);
        };
    }

    private static LocalDate fechaVencimiento(LocalDate base, FrequenciaPago freq, int periodo) {
        return switch (freq) {
            case MONTHLY -> base.plusMonths(periodo - 1);
            case WEEKLY  -> base.plusWeeks(periodo - 1);
        };
    }

    private static BigDecimal resolverTea(int cuotas, FrequenciaPago freq, ConfiguracionCrediticia config) {
        return config.getPlazos().stream()
                .filter(p -> p.getPeriodos() == cuotas && p.getFrecuencia() == freq)
                .findFirst()
                .map(PlazoTeaConfig::getTea)
                .orElse(config.getTeaDefault());
    }

    /**
     * TCEA por Newton-Raphson sobre la TIR periódica.
     *
     * Resuelve: efectivoNeto = Σ cuotaTotal_k / (1 + r)^k
     * TCEA = (1 + r)^(periodsPerYear) − 1
     *
     * @param pvNeto efectivo neto recibido por el cliente (PV0)
     */
    private static BigDecimal calcularTcea(
            BigDecimal pvNeto, List<CuotaCronograma> cronograma, FrequenciaPago freq) {

        double pv = pvNeto.doubleValue();
        if (pv <= 0.0) return BigDecimal.ZERO;

        double[] flujos = cronograma.stream()
                .mapToDouble(c -> c.getCuotaTotal().doubleValue())
                .toArray();

        // Semilla: tasa periódica esperada ≈ 1% mensual o 0.13% semanal
        double r = freq == FrequenciaPago.WEEKLY ? 0.0013 : 0.01;

        for (int iter = 0; iter < 300; iter++) {
            double f  = -pv;
            double df = 0.0;
            for (int k = 0; k < flujos.length; k++) {
                double t  = k + 1.0;
                double d  = Math.pow(1.0 + r, t);
                f  +=  flujos[k] / d;
                df -= t * flujos[k] / (d * (1.0 + r));
            }
            if (Math.abs(df) < 1e-14) break;
            double rNext = r - f / df;
            if (Math.abs(rNext - r) < 1e-10) { r = rNext; break; }
            r = rNext;
        }

        double tcea = Math.pow(1.0 + r, periodosAnio(freq)) - 1.0;
        return BigDecimal.valueOf(Math.max(tcea, 0.0)).setScale(4, RoundingMode.HALF_UP);
    }
}