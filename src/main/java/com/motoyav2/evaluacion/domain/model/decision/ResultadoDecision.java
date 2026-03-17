package com.motoyav2.evaluacion.domain.model.decision;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Resultado inmutable del MotorDeDecision.
 * Combina la decisión automática calculada con las condiciones justificadas.
 */
@Getter
@Builder
public class ResultadoDecision {

    public enum TipoDecision { APROBADO, RECHAZADO, CONDICIONAL }

    private final TipoDecision decision;
    private final double scoreFinal;
    private final String nivelRiesgo;
    private final List<String> condicionesRecomendadas;
    private final String justificacion;
    private final double porcentajeMontoRecomendado; // 1.0 = 100%, 0.8 = 80%

    public boolean esAprobado()     { return decision == TipoDecision.APROBADO; }
    public boolean esRechazado()    { return decision == TipoDecision.RECHAZADO; }
    public boolean esCondicional()  { return decision == TipoDecision.CONDICIONAL; }

    /** Estado de solicitud correspondiente a esta decisión. */
    public String toEstadoSolicitud() {
        return switch (decision) {
            case APROBADO    -> "aprobado";
            case RECHAZADO   -> "rechazado";
            case CONDICIONAL -> "condicional";
        };
    }
}
