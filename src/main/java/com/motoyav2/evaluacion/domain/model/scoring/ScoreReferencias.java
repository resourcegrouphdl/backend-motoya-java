package com.motoyav2.evaluacion.domain.model.scoring;

import lombok.Builder;
import lombok.Getter;

/**
 * Value Object del score de referencias calculado.
 * Score range: 0–100.
 */
@Getter
@Builder
public class ScoreReferencias {

    private final double valor;          // 0-100
    private final int totalReferencias;
    private final int verificadas;
    private final int noContactadas;
    private final int rechazadas;
    private final double promedioVerificadas;
    private final double penalizacionRechazadas;

    public static ScoreReferencias sinReferencias() {
        return ScoreReferencias.builder()
                .valor(0).totalReferencias(0).verificadas(0)
                .noContactadas(0).rechazadas(0)
                .promedioVerificadas(0).penalizacionRechazadas(0)
                .build();
    }
}
