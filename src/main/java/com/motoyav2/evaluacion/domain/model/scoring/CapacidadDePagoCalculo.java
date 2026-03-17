package com.motoyav2.evaluacion.domain.model.scoring;

import lombok.Builder;
import lombok.Getter;

/**
 * Value Object que representa el cálculo de capacidad de pago.
 * Evaluúa si el cliente puede asumir la cuota sin comprometer más del 35% de sus ingresos.
 */
@Getter
@Builder
public class CapacidadDePagoCalculo {

    private final double ingresoMensualEstimado;
    private final double gastosMensualesDeuda;  // deudas bancarias/12 + otras deudas/12
    private final double cuotaMensual;           // montoCuota × 2 (quincenal → mensual)
    private final double gastosFamilia;          // cargasFamiliares × umbral estimado
    private final double ingresoDisponible;      // ingreso - gastos - cuota - familia
    private final double ratioCuotaIngreso;      // cuotaMensual / ingresoMensual
    private final boolean cumpleRatio;           // ratio < 0.35
    private final String nivelCapacidad;         // ALTA | MEDIA | BAJA | INSUFICIENTE
    private final boolean ingresoEstimado;       // true si se usó rangoIngresos porque no había ingresoMensual

    public static CapacidadDePagoCalculo sinDatos() {
        return CapacidadDePagoCalculo.builder()
                .ingresoMensualEstimado(0).gastosMensualesDeuda(0)
                .cuotaMensual(0).gastosFamilia(0).ingresoDisponible(0)
                .ratioCuotaIngreso(0).cumpleRatio(false)
                .nivelCapacidad("INSUFICIENTE").ingresoEstimado(false)
                .build();
    }
}
