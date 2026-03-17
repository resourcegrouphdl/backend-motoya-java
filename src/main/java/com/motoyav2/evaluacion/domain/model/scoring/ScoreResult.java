package com.motoyav2.evaluacion.domain.model.scoring;

import lombok.Builder;
import lombok.Getter;

/**
 * Agregado de todos los scores calculados para un expediente.
 * Es el resultado completo del motor de scoring.
 */
@Getter
@Builder
public class ScoreResult {

    private final ScoreDocumental scoreDocumental;       // titular
    private final ScoreDocumental scoreGarantes;         // fiador (null si no existe)
    private final ScoreEntrevista scoreEntrevista;
    private final ScoreReferencias scoreReferencias;
    private final CapacidadDePagoCalculo capacidadDePago;

    /** Score final ponderado 0–100 */
    private final double scoreFinal;

    /** true si el expediente tiene fiador */
    private final boolean tieneFiador;

    /**
     * Pesos aplicados:
     *   Con fiador:    documental 30% | garantes 20% | entrevista 30% | referencias 20%
     *   Sin fiador:    documental 40% | entrevista 35% | referencias 25%
     */
    private final String descripcionPonderacion;

    public static ScoreResult vacio() {
        return ScoreResult.builder()
                .scoreDocumental(ScoreDocumental.cero())
                .scoreGarantes(null)
                .scoreEntrevista(ScoreEntrevista.sinEntrevista())
                .scoreReferencias(ScoreReferencias.sinReferencias())
                .capacidadDePago(CapacidadDePagoCalculo.sinDatos())
                .scoreFinal(0)
                .tieneFiador(false)
                .descripcionPonderacion("sin datos suficientes")
                .build();
    }
}
