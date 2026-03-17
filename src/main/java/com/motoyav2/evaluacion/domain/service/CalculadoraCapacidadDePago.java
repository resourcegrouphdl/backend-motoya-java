package com.motoyav2.evaluacion.domain.service;

import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.domain.model.scoring.CapacidadDePagoCalculo;
import org.springframework.stereotype.Component;

/**
 * Domain Service puro — sin I/O.
 * Calcula la capacidad de pago del titular considerando ingresos, deudas y cuota.
 */
@Component
public class CalculadoraCapacidadDePago {

    private static final double RATIO_MAXIMO = 0.35;
    private static final double GASTO_ESTIMADO_POR_CARGA = 400.0; // S/ por carga familiar

    /**
     * @param titular         persona titular
     * @param montoCuotaStr   montoCuota de la solicitud (quincenal, puede ser null)
     */
    public CapacidadDePagoCalculo calcular(Persona titular, String montoCuotaStr) {
        double ingreso = resolverIngreso(titular);
        boolean ingresoEstimado = titular.getIngresoMensualNum() == null || titular.getIngresoMensualNum() == 0;

        double deudaBancos = titular.getTotalDeudaBancos() != null ? titular.getTotalDeudaBancos() : 0;
        double otrasDeudas = titular.getTotalOtrasDeudas() != null ? titular.getTotalOtrasDeudas() : 0;
        double gastosMensualesDeuda = (deudaBancos + otrasDeudas) / 12.0;

        double cuotaQuincenal = parsearMonto(montoCuotaStr);
        double cuotaMensual = cuotaQuincenal * 2; // quincenal → mensual

        int cargas = parsearCargas(titular.getCargasFamiliares());
        double gastosFamilia = cargas * GASTO_ESTIMADO_POR_CARGA;

        double ingresoDisponible = ingreso - gastosMensualesDeuda - cuotaMensual - gastosFamilia;
        double ratio = ingreso > 0 ? cuotaMensual / ingreso : 1.0;
        boolean cumple = ratio < RATIO_MAXIMO;

        return CapacidadDePagoCalculo.builder()
                .ingresoMensualEstimado(ingreso)
                .gastosMensualesDeuda(gastosMensualesDeuda)
                .cuotaMensual(cuotaMensual)
                .gastosFamilia(gastosFamilia)
                .ingresoDisponible(ingresoDisponible)
                .ratioCuotaIngreso(roundTwo(ratio))
                .cumpleRatio(cumple)
                .nivelCapacidad(determinarNivel(ratio, ingresoDisponible))
                .ingresoEstimado(ingresoEstimado)
                .build();
    }

    /** Resuelve el ingreso mensual: usa ingresoMensualNum si existe, sino parsea rangoIngresos. */
    private double resolverIngreso(Persona titular) {
        if (titular.getIngresoMensualNum() != null && titular.getIngresoMensualNum() > 0) {
            return titular.getIngresoMensualNum();
        }
        String rango = titular.getRangoIngresos();
        if (rango != null && !rango.isBlank()) {
            return parsearRangoIngresos(rango);
        }
        String ingresoStr = titular.getIngresoMensual();
        if (ingresoStr != null && !ingresoStr.isBlank()) {
            try {
                return Double.parseDouble(ingresoStr.replaceAll("[^0-9.]", ""));
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    /** Parsea "1500-2000" → promedio 1750, o "2000" → 2000. */
    private double parsearRangoIngresos(String rango) {
        try {
            String limpio = rango.replaceAll("[^0-9\\-]", "").trim();
            if (limpio.contains("-")) {
                String[] partes = limpio.split("-");
                double min = Double.parseDouble(partes[0]);
                double max = Double.parseDouble(partes[1]);
                return (min + max) / 2.0;
            }
            return Double.parseDouble(limpio);
        } catch (Exception e) {
            return 0;
        }
    }

    private double parsearMonto(String monto) {
        if (monto == null || monto.isBlank()) return 0;
        try {
            return Double.parseDouble(monto.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parsearCargas(String cargas) {
        if (cargas == null || cargas.isBlank()) return 0;
        try {
            return Integer.parseInt(cargas.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String determinarNivel(double ratio, double ingresoDisponible) {
        if (ratio < 0.20 && ingresoDisponible > 500) return "ALTA";
        if (ratio < 0.35 && ingresoDisponible > 0)  return "MEDIA";
        if (ratio < 0.50)                            return "BAJA";
        return "INSUFICIENTE";
    }

    private double roundTwo(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
