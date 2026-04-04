package com.motoyav2.evaluacion.domain.service;

import com.motoyav2.evaluacion.domain.model.DatosCalculados;
import com.motoyav2.evaluacion.domain.model.OpcionFinanciamiento;
import com.motoyav2.evaluacion.domain.model.ResultadoCalculoFinanciamiento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Servicio de dominio puro: cálculos financieros de la solicitud.
 * Sin dependencias de infraestructura.
 */
public final class CalculadoraFinanciamientoService {

    // -------------------------------------------------------------------------
    // Configuración de negocio Motoya
    // -------------------------------------------------------------------------

    /** Gastos administrativos fijos. */
    public static final BigDecimal GASTOS_ADMINISTRATIVOS  = BigDecimal.valueOf(890);

    private static final BigDecimal ADICIONAL_FIJO         = GASTOS_ADMINISTRATIVOS;
    private static final BigDecimal INICIAL_MINIMA_PCT     = new BigDecimal("0.25");
    private static final BigDecimal MONTO_MAXIMO_FINANCIAR = BigDecimal.valueOf(5400);

    /** Tasas de interés por número de quincenas. */
    private static final Map<Integer, BigDecimal> TASAS = Map.of(
            16, new BigDecimal("0.2626"),
            20, new BigDecimal("0.3263"),
            24, new BigDecimal("0.3919")
    );

    private static final List<Integer> PLAZOS = List.of(16, 20, 24);

    private static final Map<Integer, String> RECOMENDACIONES = Map.of(
            16, "Pago Rapido",
            20, "Recomendado",
            24, "Cuota Menor"
    );

    private CalculadoraFinanciamientoService() {}

    // -------------------------------------------------------------------------
    // API principal
    // -------------------------------------------------------------------------

    /**
     * Calcula la inicial mínima considerando el tope de financiamiento.
     */
    public static BigDecimal calcularInicialMinima(BigDecimal precioMoto) {
        BigDecimal precioTotal       = precioMoto.add(ADICIONAL_FIJO);
        BigDecimal inicialMinimaPct  = precioTotal.multiply(INICIAL_MINIMA_PCT).setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoConMinima    = precioTotal.subtract(inicialMinimaPct);

        if (montoConMinima.compareTo(MONTO_MAXIMO_FINANCIAR) > 0) {
            return precioTotal.subtract(MONTO_MAXIMO_FINANCIAR).setScale(2, RoundingMode.HALF_UP);
        }
        return inicialMinimaPct;
    }

    /**
     * Calcula el precio total (moto + gastos administrativos).
     */
    public static BigDecimal calcularPrecioTotal(BigDecimal precioMoto) {
        return precioMoto.add(ADICIONAL_FIJO);
    }

    /**
     * Calcula todas las opciones de financiamiento para los plazos disponibles.
     *
     * @param precioMoto           precio del vehículo
     * @param inicialPersonalizada inicial deseada (null o cero para usar la mínima)
     */
    public static ResultadoCalculoFinanciamiento calcularFinanciamientoCompleto(
            BigDecimal precioMoto,
            BigDecimal inicialPersonalizada) {

        BigDecimal precioTotal   = precioMoto.add(ADICIONAL_FIJO);
        BigDecimal inicialMinima = calcularInicialMinima(precioMoto);
        BigDecimal inicialFinal  = (inicialPersonalizada != null
                && inicialPersonalizada.compareTo(inicialMinima) > 0)
                ? inicialPersonalizada
                : inicialMinima;

        BigDecimal montoFinanciar = precioTotal.subtract(inicialFinal);
        boolean ajustado = false;

        if (montoFinanciar.compareTo(MONTO_MAXIMO_FINANCIAR) > 0) {
            montoFinanciar = MONTO_MAXIMO_FINANCIAR;
            ajustado = true;
        }

        final BigDecimal montoFinanciarFinal = montoFinanciar;
        BigDecimal inicialReal = precioTotal.subtract(montoFinanciarFinal).setScale(2, RoundingMode.HALF_UP);

        DatosCalculados datos = DatosCalculados.builder()
                .precioMoto(precioMoto)
                .adicionalFijo(ADICIONAL_FIJO)
                .precioTotal(precioTotal)
                .inicialMinima(inicialMinima)
                .inicialFinal(inicialReal)
                .montoFinanciar(montoFinanciarFinal)
                .montoAjustadoPorTope(ajustado)
                .sumaTotal(precioTotal)
                .build();

        List<OpcionFinanciamiento> opciones = PLAZOS.stream()
                .map(q -> calcularOpcion(q, montoFinanciarFinal, inicialReal))
                .toList();

        return ResultadoCalculoFinanciamiento.builder()
                .datosCalculados(datos)
                .opciones(opciones)
                .inicialMinimaCalculada(inicialMinima)
                .build();
    }

    /**
     * Calcula una opción específica de financiamiento.
     *
     * @return la opción, o null si el plazo no está disponible
     */
    public static OpcionFinanciamiento calcularOpcionEspecifica(
            BigDecimal precioMoto,
            BigDecimal inicialPersonalizada,
            int quincenas) {

        if (!PLAZOS.contains(quincenas)) return null;

        return calcularFinanciamientoCompleto(precioMoto, inicialPersonalizada)
                .getOpciones().stream()
                .filter(op -> op.getQuincenas() == quincenas)
                .findFirst()
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // Validaciones
    // -------------------------------------------------------------------------

    public static boolean validarPrecioMoto(BigDecimal precioMoto) {
        return precioMoto != null && precioMoto.compareTo(BigDecimal.valueOf(1000)) >= 0;
    }

    public static boolean validarInicial(BigDecimal precioMoto, BigDecimal inicial) {
        return inicial != null && inicial.compareTo(calcularInicialMinima(precioMoto)) >= 0;
    }

    // -------------------------------------------------------------------------
    // Getters de configuración
    // -------------------------------------------------------------------------

    public static BigDecimal getMontoMaximoFinanciar()    { return MONTO_MAXIMO_FINANCIAR; }
    public static BigDecimal getPorcentajeInicialMinima() { return INICIAL_MINIMA_PCT.multiply(BigDecimal.valueOf(100)); }
    public static BigDecimal getTasaInteres(int quincenas){ return TASAS.get(quincenas); }
    public static List<Integer> getOpcionesQuincenas()    { return PLAZOS; }
    public static List<Integer> getOpcionesMeses()        { return PLAZOS.stream().map(q -> q / 2).toList(); }

    // -------------------------------------------------------------------------
    // Métodos de compatibilidad (usados por use cases existentes)
    // -------------------------------------------------------------------------

    /**
     * Cuota quincenal con interés lineal.
     * Fórmula: (montoFinanciar * (1 + tasa)) / plazoQuincenas
     * Requiere plazoQuincenas en [16, 20, 24].
     */
    public static BigDecimal calcularCuotaQuincenal(
            BigDecimal precioMoto,
            BigDecimal inicial,
            int plazoQuincenas) {
        if (plazoQuincenas <= 0) throw new IllegalArgumentException("El plazo debe ser mayor a cero");
        BigDecimal tasa = TASAS.get(plazoQuincenas);
        if (tasa == null) throw new IllegalArgumentException("Plazo no disponible: " + plazoQuincenas);
        BigDecimal montoFinanciar = precioMoto.add(GASTOS_ADMINISTRATIVOS).subtract(inicial);
        BigDecimal montoTotal = montoFinanciar.multiply(BigDecimal.ONE.add(tasa));
        return montoTotal.divide(BigDecimal.valueOf(plazoQuincenas), 2, RoundingMode.HALF_UP);
    }

    /**
     * Total a pagar (inicial + todas las cuotas).
     * Fórmula: inicial + (cuotaQuincenal * plazoQuincenas)
     */
    public static BigDecimal calcularTotalAPagar(
            BigDecimal inicial,
            BigDecimal cuotaQuincenal,
            int plazoQuincenas) {
        return inicial.add(cuotaQuincenal.multiply(BigDecimal.valueOf(plazoQuincenas)));
    }

    // -------------------------------------------------------------------------
    // Helpers internos
    // -------------------------------------------------------------------------

    private static OpcionFinanciamiento calcularOpcion(
            int quincenas,
            BigDecimal montoFinanciar,
            BigDecimal inicialFinal) {

        BigDecimal tasa         = TASAS.get(quincenas);
        BigDecimal interesTotal = montoFinanciar.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoTotal   = montoFinanciar.add(interesTotal);
        BigDecimal sumaTotal    = montoTotal.add(inicialFinal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cuotaQncl    = montoTotal.divide(BigDecimal.valueOf(quincenas), 2, RoundingMode.HALF_UP);
        BigDecimal cuotaMens    = cuotaQncl.multiply(BigDecimal.valueOf(2)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal tea = BigDecimal.ZERO;
        if (montoFinanciar.compareTo(BigDecimal.ZERO) > 0) {
            double factor = cuotaQncl.divide(montoFinanciar, 10, RoundingMode.HALF_UP).doubleValue();
            tea = BigDecimal.valueOf((Math.pow(factor, 24) - 1) * 100).setScale(2, RoundingMode.HALF_UP);
        }

        String popularidad = quincenas == 16 ? "rapido" : quincenas == 24 ? "economico" : "popular";

        return OpcionFinanciamiento.builder()
                .plazo(quincenas / 2)
                .quincenas(quincenas)
                .tasa(tasa)
                .tasaPorcentaje(tasa.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))
                .interesTotal(interesTotal)
                .montoTotalPagar(montoTotal.setScale(2, RoundingMode.HALF_UP))
                .sumaTotal(sumaTotal)
                .cuotaQuincenal(cuotaQncl)
                .cuotaMensual(cuotaMens)
                .tea(tea)
                .recomendacion(RECOMENDACIONES.get(quincenas))
                .popularidad(popularidad)
                .build();
    }
}
