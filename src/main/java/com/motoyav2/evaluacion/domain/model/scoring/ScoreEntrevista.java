package com.motoyav2.evaluacion.domain.model.scoring;

import lombok.Builder;
import lombok.Getter;

/**
 * Value Object del score de entrevista calculado.
 * Score range: 0–100.
 */
@Getter
@Builder
public class ScoreEntrevista {

    private final double valor;                 // 0-100
    private final double factorPresentacion;    // presentacionPersonal (1-5) × peso
    private final double factorActitud;         // actitudColaboracion (1-5) × peso
    private final double factorCoherencia;      // coherenciaRespuestas (1-5) × peso
    private final double factorConfianza;       // nivelConfianza (1-5) × peso
    private final double penalizacionPuntualidad;
    private final double penalizacionAlertas;
    private final int alertasCriticas;
    private final int alertasAltas;
    private final boolean entrevistaRealizada;
    private final String recomendacion;         // aprobar|rechazar|condicional|requiere_comite|revisar

    public static ScoreEntrevista sinEntrevista() {
        return ScoreEntrevista.builder()
                .valor(0).entrevistaRealizada(false)
                .factorPresentacion(0).factorActitud(0)
                .factorCoherencia(0).factorConfianza(0)
                .penalizacionPuntualidad(0).penalizacionAlertas(0)
                .alertasCriticas(0).alertasAltas(0)
                .recomendacion(null)
                .build();
    }
}
