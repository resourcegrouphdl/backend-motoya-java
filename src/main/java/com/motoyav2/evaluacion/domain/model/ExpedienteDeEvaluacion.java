package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ExpedienteDeEvaluacion {

    private final Evaluacion evaluacion;
    private final Persona titular;
    private final Persona fiador;
    private final Vehiculo vehiculo;
    private final Financiamiento financiamiento;
    private final List<ReferenciasDelTitular> referencias;

    public static ExpedienteDeEvaluacion crear(
            Evaluacion evaluacion,
            Persona titular,
            Persona fiador,
            Vehiculo vehiculo,
            Financiamiento financiamiento,
            List<ReferenciasDelTitular> referencias) {

        if (evaluacion == null) throw new IllegalArgumentException("La evaluación es requerida");
        if (titular == null) throw new IllegalArgumentException("El titular es requerido");
        if (vehiculo == null) throw new IllegalArgumentException("El vehículo es requerido");

        return ExpedienteDeEvaluacion.builder()
                .evaluacion(evaluacion)
                .titular(titular)
                .fiador(fiador)
                .vehiculo(vehiculo)
                .financiamiento(financiamiento)
                .referencias(referencias != null ? List.copyOf(referencias) : List.of())
                .build();
    }
}
