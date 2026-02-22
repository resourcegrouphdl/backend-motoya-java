package com.motoyav2.evaluacion.domain.policy;

import com.motoyav2.evaluacion.domain.model.Evaluacion;
import com.motoyav2.evaluacion.domain.model.Financiamiento;
import com.motoyav2.evaluacion.domain.model.Vehiculo;

import java.time.Instant;

public final class FinanciamientoInicialPolicy {

    private FinanciamientoInicialPolicy() {}

    public static Financiamiento calcular(
            Evaluacion evaluacion,
            Vehiculo vehiculo,
            String montoCuota,
            String plazoQuincenas,
            String precioCompraMoto) {

        String montoVehiculo = precioCompraMoto != null
                ? precioCompraMoto
                : vehiculo.getPrecioReferencial();

        return Financiamiento.builder()
                .evaluacionId(evaluacion.getCodigoDeSolicitud())
                .montoDelVehiculo(montoVehiculo)
                .montoCuotaQuincenal(montoCuota)
                .numeroCuotasQuincenales(plazoQuincenas)
                .creadoEn(Instant.now().toString())
                .build();
    }
}
