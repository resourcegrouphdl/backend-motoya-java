package com.motoyav2.financiamiento.domain.service;

import com.motoyav2.financiamiento.domain.model.CuotaCronograma;
import com.motoyav2.financiamiento.domain.model.ResultadoSimulacion;
import com.motoyav2.financiamiento.domain.model.SolicitudSimulacion;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor financiero puro — sistema de amortización francés (cuota fija).
 *
 * <p><strong>Modelo matemático:</strong>
 * <pre>
 *   montoFinanciado = precioVehiculo + gastosAdministrativos - cuotaInicial
 *   i = (1 + TEA)^(1/24) - 1                          [tasa quincenal]
 *   cuota = P * i*(1+i)^n / ((1+i)^n - 1)             [PMT francés]
 *   interes_k = saldo_{k-1} * i
 *   amortizacion_k = cuota - interes_k
 *   saldo_k = saldo_{k-1} - amortizacion_k
 *   TCEA = (1 + r_irr)^24 - 1                          [via Newton-Raphson]
 * </pre>
 *
 * <p>Clase stateless, sin dependencias de infraestructura.
 */
public final class MotorFinancieroService {

    // -------------------------------------------------------------------------
    // Constantes de negocio
    // -------------------------------------------------------------------------

    /** Gastos administrativos por defecto (S/ 890). */
    public static final BigDecimal GASTOS_ADMINISTRATIVOS_DEFAULT = BigDecimal.valueOf(890);

    /**
     * TEA por defecto mientras el área comercial define la tasa oficial.
     * ⚠️ Confirmar con el equipo financiero antes de usar en producción.
     */
    public static final BigDecimal TEA_DEFAULT = new BigDecimal("0.60");

    /** Porcentaje mínimo de cuota inicial sobre precio del vehículo (20%). */
    private static final BigDecimal INICIAL_MINIMA_PCT = new BigDecimal("0.20");

    /** Periodos quincenales por año (para conversión de tasa). */
    private static final int PERIODOS_ANIO = 24;

    /** Precisión interna de cálculo (15 dígitos significativos). */
    private static final MathContext MC = new MathContext(15, RoundingMode.HALF_UP);

    private MotorFinancieroService() {}

    // =========================================================================
    // API PÚBLICA
    // =========================================================================

    /**
     * Simula un crédito completo: cuota fija, cronograma y TCEA.
     *
     * @param solicitud parámetros de la simulación
     * @return resultado con cuota, cronograma completo y TCEA
     * @throws IllegalArgumentException si los parámetros no son válidos
     */
    public static ResultadoSimulacion simular(SolicitudSimulacion solicitud) {
        validar(solicitud);

        BigDecimal gastos  = resolverGastos(solicitud.getGastosAdministrativos());
        BigDecimal monto   = calcularMontoFinanciado(solicitud.getPrecioVehiculo(),
                                                     solicitud.getCuotaInicial(), gastos);
        BigDecimal i       = tasaQuincenal(solicitud.getTea());
        int        n       = solicitud.getNumeroCuotas();

        BigDecimal cuota   = pmt(monto, i, n);
        List<CuotaCronograma> cronograma = generarCronograma(monto, i, n, cuota);

        BigDecimal totalIntereses = cronograma.stream()
                .map(CuotaCronograma::getInteres)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalPagar = solicitud.getCuotaInicial()
                .add(cronograma.stream()
                        .map(CuotaCronograma::getCuota)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal montoNeto = solicitud.getPrecioVehiculo()
                .subtract(solicitud.getCuotaInicial());
        BigDecimal tcea = calcularTcea(montoNeto, cuota, n, i.doubleValue());

        return ResultadoSimulacion.builder()
                .precioVehiculo(solicitud.getPrecioVehiculo())
                .cuotaInicial(solicitud.getCuotaInicial())
                .gastosAdministrativos(gastos)
                .numeroCuotas(n)
                .tea(solicitud.getTea())
                .montoFinanciado(monto.setScale(2, RoundingMode.HALF_UP))
                .tasaQuincenal(i.setScale(8, RoundingMode.HALF_UP))
                .cuotaQuincenal(cuota)
                .totalIntereses(totalIntereses)
                .totalPagar(totalPagar)
                .tcea(tcea)
                .cronograma(cronograma)
                .build();
    }

    /**
     * Simula múltiples plazos (sin cronograma) para presentar opciones al cliente.
     *
     * @param precioVehiculo precio del vehículo
     * @param cuotaInicial   cuota inicial
     * @param tea            TEA del crédito
     * @param plazos         lista de plazos en quincenas a simular
     * @return lista de resultados, uno por plazo
     */
    public static List<ResultadoSimulacion> simularOpciones(BigDecimal precioVehiculo,
                                                             BigDecimal cuotaInicial,
                                                             BigDecimal tea,
                                                             List<Integer> plazos) {
        return plazos.stream()
                .map(plazo -> simular(SolicitudSimulacion.builder()
                        .precioVehiculo(precioVehiculo)
                        .cuotaInicial(cuotaInicial)
                        .numeroCuotas(plazo)
                        .tea(tea)
                        .build()))
                .toList();
    }

    /**
     * Calcula únicamente la cuota fija para un monto, tasa y plazo dados.
     *
     * @param montoFinanciado monto a financiar
     * @param tea             TEA
     * @param numeroCuotas    número de cuotas quincenales
     * @return cuota quincenal fija (scale 2)
     */
    public static BigDecimal calcularCuota(BigDecimal montoFinanciado, BigDecimal tea, int numeroCuotas) {
        BigDecimal i = tasaQuincenal(tea);
        return pmt(montoFinanciado, i, numeroCuotas);
    }

    /**
     * Genera únicamente el cronograma de amortización.
     *
     * @param montoFinanciado monto a financiar
     * @param tea             TEA
     * @param numeroCuotas    número de cuotas quincenales
     * @return cronograma completo
     */
    public static List<CuotaCronograma> generarCronograma(BigDecimal montoFinanciado,
                                                           BigDecimal tea,
                                                           int numeroCuotas) {
        BigDecimal i     = tasaQuincenal(tea);
        BigDecimal cuota = pmt(montoFinanciado, i, numeroCuotas);
        return generarCronograma(montoFinanciado, i, numeroCuotas, cuota);
    }

    /**
     * Calcula la cuota inicial mínima (20% del precio del vehículo).
     */
    public static BigDecimal calcularInicialMinima(BigDecimal precioVehiculo) {
        return precioVehiculo.multiply(INICIAL_MINIMA_PCT).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Convierte una TEA a tasa quincenal periódica.
     * i = (1 + TEA)^(1/24) - 1
     */
    public static BigDecimal tasaQuincenal(BigDecimal tea) {
        double iDouble = Math.pow(1.0 + tea.doubleValue(), 1.0 / PERIODOS_ANIO) - 1.0;
        return BigDecimal.valueOf(iDouble).setScale(10, RoundingMode.HALF_UP);
    }

    // =========================================================================
    // VALIDACIONES
    // =========================================================================

    public static void validar(SolicitudSimulacion s) {
        if (s.getPrecioVehiculo() == null || s.getPrecioVehiculo().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("precioVehiculo debe ser mayor a cero");

        if (s.getCuotaInicial() == null || s.getCuotaInicial().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("cuotaInicial no puede ser negativa");

        BigDecimal inicialMinima = calcularInicialMinima(s.getPrecioVehiculo());
        if (s.getCuotaInicial().compareTo(inicialMinima) < 0)
            throw new IllegalArgumentException(
                    "cuotaInicial debe ser al menos el 20%% del precio del vehículo (S/ "
                    + inicialMinima.setScale(2, RoundingMode.HALF_UP) + ")");

        if (s.getNumeroCuotas() < 1)
            throw new IllegalArgumentException("numeroCuotas debe ser al menos 1");

        if (s.getTea() == null || s.getTea().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("tea debe ser mayor a cero (ej: 0.60 para 60%%)");

        if (s.getTea().compareTo(BigDecimal.TEN) >= 0)
            throw new IllegalArgumentException("tea parece incorrecta (mayor al 1000%%). Use decimal: 0.60 = 60%%");

        BigDecimal gastos = resolverGastos(s.getGastosAdministrativos());
        BigDecimal costoTotal = s.getPrecioVehiculo().add(gastos);
        if (s.getCuotaInicial().compareTo(costoTotal) >= 0)
            throw new IllegalArgumentException("cuotaInicial no puede ser mayor o igual al costo total");
    }

    // =========================================================================
    // CÁLCULOS INTERNOS
    // =========================================================================

    /**
     * PMT (cuota fija): P * i*(1+i)^n / ((1+i)^n - 1)
     */
    private static BigDecimal pmt(BigDecimal P, BigDecimal i, int n) {
        BigDecimal unoPlusI  = BigDecimal.ONE.add(i);
        BigDecimal potencia  = unoPlusI.pow(n, MC);                       // (1+i)^n
        BigDecimal numerador = P.multiply(i, MC).multiply(potencia, MC);  // P * i * (1+i)^n
        BigDecimal denom     = potencia.subtract(BigDecimal.ONE, MC);     // (1+i)^n - 1
        return numerador.divide(denom, MC).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Genera el cronograma de amortización.
     * La última cuota se ajusta para cerrar el saldo exactamente en cero.
     */
    private static List<CuotaCronograma> generarCronograma(BigDecimal P, BigDecimal i,
                                                             int n, BigDecimal cuota) {
        List<CuotaCronograma> cronograma = new ArrayList<>(n);
        BigDecimal saldo = P.setScale(2, RoundingMode.HALF_UP);

        for (int k = 1; k <= n; k++) {
            BigDecimal interes = saldo.multiply(i, MC).setScale(2, RoundingMode.HALF_UP);

            if (k == n) {
                // Última cuota: amortiza el saldo restante exacto (absorbe redondeos)
                BigDecimal cuotaFinal = saldo.add(interes).setScale(2, RoundingMode.HALF_UP);
                cronograma.add(CuotaCronograma.builder()
                        .numero(k)
                        .saldoInicial(saldo)
                        .interes(interes)
                        .amortizacion(saldo)
                        .cuota(cuotaFinal)
                        .saldoFinal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .build());
            } else {
                BigDecimal amortizacion = cuota.subtract(interes).setScale(2, RoundingMode.HALF_UP);
                BigDecimal saldoFinal   = saldo.subtract(amortizacion).setScale(2, RoundingMode.HALF_UP);

                cronograma.add(CuotaCronograma.builder()
                        .numero(k)
                        .saldoInicial(saldo)
                        .interes(interes)
                        .amortizacion(amortizacion)
                        .cuota(cuota)
                        .saldoFinal(saldoFinal)
                        .build());

                saldo = saldoFinal.max(BigDecimal.ZERO);
            }
        }
        return cronograma;
    }

    /**
     * TCEA vía Newton-Raphson (IRR).
     *
     * <p>Desde la perspectiva del cliente, el monto neto recibido es
     * (precioVehiculo - cuotaInicial). El TCEA es la tasa anual que iguala
     * ese flujo con los pagos quincenales:
     * <pre>
     *   montoNeto = sum( cuota / (1+r)^k,  k=1..n )
     *   TCEA = (1 + r)^24 - 1
     * </pre>
     *
     * @param montoNeto   precioVehiculo - cuotaInicial
     * @param cuota       cuota quincenal fija
     * @param n           número de cuotas
     * @param initialGuess tasa quincenal inicial para Newton (usar i del TEA)
     * @return TCEA expresado como porcentaje (ej: 72.35 = 72.35%)
     */
    private static BigDecimal calcularTcea(BigDecimal montoNeto, BigDecimal cuota,
                                            int n, double initialGuess) {
        if (montoNeto.compareTo(BigDecimal.ZERO) <= 0) {
            // Caso extremo: cuotaInicial >= precioVehiculo → TCEA = TEA (solo gastos)
            return BigDecimal.ZERO;
        }

        double c  = cuota.doubleValue();
        double pv = montoNeto.doubleValue();
        double r  = initialGuess;

        for (int iter = 0; iter < 300; iter++) {
            double f  = 0.0;
            double df = 0.0;
            for (int k = 1; k <= n; k++) {
                double factor = Math.pow(1.0 + r, k);
                f  += c / factor;
                df -= k * c / (factor * (1.0 + r));
            }
            f -= pv;

            if (Math.abs(f) < 1e-10) break;
            if (Math.abs(df) < 1e-15) break;

            double rNuevo = r - f / df;
            r = (rNuevo < 1e-8) ? 1e-8 : rNuevo; // previene tasas negativas
        }

        double tcea = (Math.pow(1.0 + r, PERIODOS_ANIO) - 1.0) * 100.0;
        return BigDecimal.valueOf(tcea).setScale(4, RoundingMode.HALF_UP);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static BigDecimal calcularMontoFinanciado(BigDecimal precio,
                                                       BigDecimal cuotaInicial,
                                                       BigDecimal gastos) {
        return precio.add(gastos).subtract(cuotaInicial);
    }

    private static BigDecimal resolverGastos(BigDecimal gastos) {
        return (gastos != null && gastos.compareTo(BigDecimal.ZERO) > 0)
                ? gastos
                : GASTOS_ADMINISTRATIVOS_DEFAULT;
    }
}
