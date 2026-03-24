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
 * Cumple con la normativa peruana:
 *   - Resolución SBS N° 11356-2008: divulgación de TEA y TCEA
 *   - Ley 26702 (Ley General del Sistema Financiero): cálculo de seguros
 *   - Circular SBS B-2216-2010: cronograma de pagos obligatorio
 *
 * Fórmulas:
 *   TEM  = (1 + TEA)^(1/12) − 1
 *   Cuota_base = PV × TEM / (1 − (1 + TEM)^−n)
 *   Interés_i  = Saldo_{i−1} × TEM
 *   Amort_i    = Cuota_base − Interés_i
 *   Seguro_i   = Saldo_{i−1} × tasa_seguro_mensual
 *   TCEA: TIR de los flujos {cuota_base + seguro_i} calculada por Newton-Raphson
 */
public final class MotorCalculoCrediticio {

    private static final MathContext MC    = MathContext.DECIMAL128;
    private static final int         SCALE = 6;

    private MotorCalculoCrediticio() {}

    // =========================================================================
    // API pública
    // =========================================================================

    public static ResultadoSimulacion calcular(
            ParametrosSimulacion params,
            ConfiguracionCrediticia config) {

        BigDecimal precioVehiculo = params.getPrecioVehiculo();
        BigDecimal gastosAdmin    = config.getGastosAdministrativos();
        BigDecimal precioTotal    = precioVehiculo.add(gastosAdmin);

        // ── Inicial mínima ────────────────────────────────────────────────────
        BigDecimal inicialMinimaPct  = precioTotal.multiply(config.getPorcentajeInicialMinima(), MC);
        BigDecimal montoConMinima    = precioTotal.subtract(inicialMinimaPct);
        BigDecimal inicialMinima;
        if (montoConMinima.compareTo(config.getMontoMaximoFinanciar()) > 0) {
            inicialMinima = precioTotal.subtract(config.getMontoMaximoFinanciar())
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            inicialMinima = inicialMinimaPct.setScale(2, RoundingMode.HALF_UP);
        }

        // ── Inicial aplicada ──────────────────────────────────────────────────
        BigDecimal inicialAplicada = (params.getInicial() != null
                && params.getInicial().compareTo(inicialMinima) >= 0)
                ? params.getInicial().setScale(2, RoundingMode.HALF_UP)
                : inicialMinima;

        BigDecimal montoFinanciar  = precioTotal.subtract(inicialAplicada).setScale(2, RoundingMode.HALF_UP);
        boolean    ajustado        = false;

        if (montoFinanciar.compareTo(config.getMontoMaximoFinanciar()) > 0) {
            montoFinanciar  = config.getMontoMaximoFinanciar();
            inicialAplicada = precioTotal.subtract(montoFinanciar).setScale(2, RoundingMode.HALF_UP);
            ajustado        = true;
        }

        // ── TEA y TEM ─────────────────────────────────────────────────────────
        BigDecimal tea    = params.getTeaOverride() != null
                ? params.getTeaOverride()
                : resolverTea(params.getPlazoMeses(), config);
        double     teaD   = tea.doubleValue();
        double     temD   = Math.pow(1.0 + teaD, 1.0 / 12.0) - 1.0;
        BigDecimal tem    = BigDecimal.valueOf(temD).setScale(SCALE, RoundingMode.HALF_UP);

        int        n             = params.getPlazoMeses();
        BigDecimal pv            = montoFinanciar;
        BigDecimal comision      = config.getComisionDesembolso();
        BigDecimal tasaSeguro    = params.isIncluirSeguro()
                ? config.getTasaSeguroDesgravamenMensual()
                : BigDecimal.ZERO;

        // ── Cuota base (Sistema Francés) ──────────────────────────────────────
        BigDecimal cuotaBase;
        if (temD == 0.0) {
            cuotaBase = pv.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        } else {
            double pvD     = pv.doubleValue();
            double cuotaD  = pvD * temD / (1.0 - Math.pow(1.0 + temD, -n));
            cuotaBase      = BigDecimal.valueOf(cuotaD).setScale(2, RoundingMode.HALF_UP);
        }

        // ── Cronograma de pagos ───────────────────────────────────────────────
        List<CuotaCronograma> cronograma        = new ArrayList<>(n);
        BigDecimal            saldo             = pv;
        BigDecimal            totalIntereses    = BigDecimal.ZERO;
        BigDecimal            totalSeguroAcum   = BigDecimal.ZERO;
        BigDecimal            totalCuotasAcum   = BigDecimal.ZERO;
        LocalDate             fechaBase         = LocalDate.now().plusMonths(1).withDayOfMonth(1);

        for (int i = 1; i <= n; i++) {
            BigDecimal saldoInicial  = saldo.setScale(2, RoundingMode.HALF_UP);
            BigDecimal interesMes    = saldoInicial.multiply(tem, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal amortizacion;
            BigDecimal cuotaFila;
            BigDecimal saldoFinal;

            if (i == n) {
                // Último pago: salda exactamente el saldo pendiente
                amortizacion = saldoInicial;
                cuotaFila    = interesMes.add(amortizacion);
                saldoFinal   = BigDecimal.ZERO;
            } else {
                amortizacion = cuotaBase.subtract(interesMes).setScale(2, RoundingMode.HALF_UP);
                cuotaFila    = cuotaBase;
                saldoFinal   = saldoInicial.subtract(amortizacion).setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal seguroMes   = saldoInicial.multiply(tasaSeguro, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cuotaTotal  = cuotaFila.add(seguroMes);

            cronograma.add(CuotaCronograma.builder()
                    .numeroCuota(i)
                    .fechaVencimiento(fechaBase.plusMonths(i - 1))
                    .saldoInicial(saldoInicial)
                    .interes(interesMes)
                    .amortizacion(amortizacion)
                    .seguroDesgravamen(seguroMes)
                    .cuotaTotal(cuotaTotal)
                    .saldoFinal(saldoFinal)
                    .build());

            saldo           = saldoFinal;
            totalIntereses  = totalIntereses.add(interesMes);
            totalSeguroAcum = totalSeguroAcum.add(seguroMes);
            totalCuotasAcum = totalCuotasAcum.add(cuotaTotal);
        }

        BigDecimal totalAPagar = inicialAplicada
                .add(pv)
                .add(totalIntereses)
                .add(totalSeguroAcum)
                .add(comision)
                .setScale(2, RoundingMode.HALF_UP);

        // ── TCEA (TIR sobre flujos reales incluyendo seguro y comisión) ───────
        BigDecimal tcea = calcularTcea(pv.subtract(comision), cronograma);

        // Cuota promedio total (el seguro varía mes a mes con el saldo)
        BigDecimal cuotaTotalPromedio = totalCuotasAcum
                .divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);

        return ResultadoSimulacion.builder()
                .precioVehiculo(precioVehiculo.setScale(2, RoundingMode.HALF_UP))
                .gastosAdministrativos(gastosAdmin.setScale(2, RoundingMode.HALF_UP))
                .precioTotal(precioTotal.setScale(2, RoundingMode.HALF_UP))
                .inicialMinima(inicialMinima)
                .inicialAplicada(inicialAplicada)
                .montoFinanciar(pv)
                .plazoMeses(n)
                .comisionDesembolso(comision.setScale(2, RoundingMode.HALF_UP))
                .tea(tea)
                .tem(tem)
                .tcea(tcea)
                .cuotaBaseMensual(cuotaBase)
                .cuotaTotalMensual(cuotaTotalPromedio)
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

    private static BigDecimal resolverTea(int meses, ConfiguracionCrediticia config) {
        return config.getPlazos().stream()
                .filter(p -> p.getMeses() == meses)
                .findFirst()
                .map(PlazoTeaConfig::getTea)
                .orElse(config.getTeaDefault());
    }

    /**
     * Calcula la TCEA mediante Newton-Raphson sobre la TIR mensual.
     * TCEA = (1 + TIR_mensual)^12 − 1
     *
     * @param pvNeto monto neto recibido por el cliente (PV − comisión desembolso)
     */
    private static BigDecimal calcularTcea(BigDecimal pvNeto, List<CuotaCronograma> cronograma) {
        double pv = pvNeto.doubleValue();
        if (pv <= 0.0) return BigDecimal.ZERO;

        double[] flujos = cronograma.stream()
                .mapToDouble(c -> c.getCuotaTotal().doubleValue())
                .toArray();

        // Newton-Raphson — semilla: ~1% mensual
        double r = 0.01;
        for (int iter = 0; iter < 200; iter++) {
            double f  = -pv;
            double df = 0.0;
            for (int i = 0; i < flujos.length; i++) {
                double t  = i + 1.0;
                double d  = Math.pow(1.0 + r, t);
                f  +=  flujos[i] / d;
                df -= t * flujos[i] / (d * (1.0 + r));
            }
            if (Math.abs(df) < 1e-14) break;
            double rNext = r - f / df;
            if (Math.abs(rNext - r) < 1e-10) { r = rNext; break; }
            r = rNext;
        }

        double tcea = Math.pow(1.0 + r, 12.0) - 1.0;
        return BigDecimal.valueOf(Math.max(tcea, 0.0)).setScale(4, RoundingMode.HALF_UP);
    }
}