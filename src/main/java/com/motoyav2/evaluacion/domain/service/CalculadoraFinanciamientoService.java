package com.motoyav2.evaluacion.domain.service;

import com.motoyav2.evaluacion.domain.model.DatosFinancieros;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Servicio de dominio puro: cálculos financieros de la solicitud.
 * Sin dependencias de infraestructura.
 */
public final class CalculadoraFinanciamientoService {

    /** Gastos administrativos fijos (constante de negocio Motoya). */
    public static final BigDecimal GASTOS_ADMINISTRATIVOS = BigDecimal.valueOf(890);

    private CalculadoraFinanciamientoService() {}

    /**
     * Calcula la cuota quincenal.
     * Fórmula: cuota = (precioMoto + gastosAdmin - inicial) / plazoQuincenas
     */
    public static BigDecimal calcularCuotaQuincenal(
            BigDecimal precioMoto,
            BigDecimal inicial,
            int plazoQuincenas) {
        if (plazoQuincenas <= 0) {
            throw new IllegalArgumentException("El plazo debe ser mayor a cero");
        }
        BigDecimal monto = precioMoto.add(GASTOS_ADMINISTRATIVOS).subtract(inicial);
        return monto.divide(BigDecimal.valueOf(plazoQuincenas), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el total a pagar.
     * Fórmula: totalAPagar = inicial + (cuota * plazoQuincenas)
     */
    public static BigDecimal calcularTotalAPagar(
            BigDecimal inicial,
            BigDecimal cuotaQuincenal,
            int plazoQuincenas) {
        return inicial.add(cuotaQuincenal.multiply(BigDecimal.valueOf(plazoQuincenas)));
    }

    /**
     * Recalcula datos financieros completos dado nuevos parámetros del evaluador.
     */
    public static DatosFinancieros recalcular(
            DatosFinancieros base,
            BigDecimal nuevaInicial,
            int nuevoPlazo) {
        BigDecimal costoTotal = base.getCostoTotal() != null ? base.getCostoTotal()
                : base.getMontoVehiculo() != null ? base.getMontoVehiculo().add(GASTOS_ADMINISTRATIVOS)
                : BigDecimal.ZERO;

        BigDecimal montoFinanciar = costoTotal.subtract(nuevaInicial);
        BigDecimal cuota = nuevoPlazo > 0
                ? montoFinanciar.divide(BigDecimal.valueOf(nuevoPlazo), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal total = nuevaInicial.add(cuota.multiply(BigDecimal.valueOf(nuevoPlazo)));
        BigDecimal pct = costoTotal.compareTo(BigDecimal.ZERO) > 0
                ? nuevaInicial.divide(costoTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return base.toBuilder()
                .inicial(nuevaInicial)
                .montoFinanciar(montoFinanciar)
                .numeroCuotasQuincenales(nuevoPlazo)
                .montoCuotaQuincenal(cuota)
                .totalAPagar(total)
                .porcentajeInicial(pct)
                .build();
    }
}
