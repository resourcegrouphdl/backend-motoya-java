package com.motoyav2.evaluacion.domain.service;

import com.motoyav2.evaluacion.domain.model.decision.ResultadoDecision;
import com.motoyav2.evaluacion.domain.model.decision.ResultadoDecision.TipoDecision;
import com.motoyav2.evaluacion.domain.model.riesgo.NivelRiesgo;
import com.motoyav2.evaluacion.domain.model.riesgo.PerfilRiesgo;
import com.motoyav2.evaluacion.domain.model.scoring.ScoreResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain Service puro — sin I/O.
 * Aplica las reglas de negocio para determinar la decisión final de crédito.
 *
 * Tabla de decisión:
 * ┌──────────────┬────────────────┬──────────────────────────┐
 * │ Score Final  │ Nivel Riesgo   │ Decisión                 │
 * ├──────────────┼────────────────┼──────────────────────────┤
 * │ cualquiera   │ CRITICO        │ RECHAZADO                │
 * │ < 40         │ cualquiera     │ RECHAZADO                │
 * │ >= 70        │ BAJO           │ APROBADO  (100% monto)   │
 * │ >= 65        │ BAJO           │ APROBADO  (100% monto)   │
 * │ >= 70        │ MEDIO          │ CONDICIONAL (90% monto)  │
 * │ >= 60        │ BAJO/MEDIO     │ CONDICIONAL (90% monto)  │
 * │ >= 50        │ BAJO/MEDIO     │ CONDICIONAL (80% monto)  │
 * │ >= 60        │ ALTO           │ CONDICIONAL (70% monto)  │
 * │ < 60         │ ALTO           │ RECHAZADO                │
 * │ cualquiera   │ CRITICO        │ RECHAZADO                │
 * └──────────────┴────────────────┴──────────────────────────┘
 */
@Component
public class MotorDeDecision {

    public ResultadoDecision evaluar(ScoreResult scores, PerfilRiesgo perfil) {
        double score = scores.getScoreFinal();
        NivelRiesgo riesgo = perfil.getNivelGeneral();

        // Rechazo inmediato
        if (riesgo == NivelRiesgo.CRITICO) {
            return buildRechazado(score, riesgo, "Perfil de riesgo CRÍTICO — flags críticos detectados: " + perfil.getFlagsCriticos());
        }
        if (score < 40) {
            return buildRechazado(score, riesgo, "Score final insuficiente (" + score + " < 40 puntos mínimos)");
        }

        // Aprobado directo
        if (score >= 65 && riesgo == NivelRiesgo.BAJO) {
            return buildAprobado(score, riesgo, 1.0, "Score alto con riesgo bajo — aprobación directa");
        }

        // Condicional con distintos porcentajes de monto
        if (score >= 70 && riesgo == NivelRiesgo.MEDIO) {
            List<String> conds = List.of(
                "Presentar aval adicional o garantía",
                "Verificar estabilidad laboral documentada"
            );
            return buildCondicional(score, riesgo, 0.90, conds, "Score alto pero riesgo medio");
        }
        if (score >= 60 && (riesgo == NivelRiesgo.BAJO || riesgo == NivelRiesgo.MEDIO)) {
            List<String> conds = List.of("Completar verificación de referencias pendientes");
            return buildCondicional(score, riesgo, 0.90, conds, "Score aceptable — condiciones menores");
        }
        if (score >= 50 && (riesgo == NivelRiesgo.BAJO || riesgo == NivelRiesgo.MEDIO)) {
            List<String> conds = List.of(
                "Reducir monto solicitado al 80%",
                "Presentar fiador con perfil crediticio limpio",
                "Verificar ingresos con documentación adicional"
            );
            return buildCondicional(score, riesgo, 0.80, conds, "Score bajo — condiciones significativas");
        }
        if (score >= 60 && riesgo == NivelRiesgo.ALTO) {
            List<String> conds = List.of(
                "Reducir monto al 70% y ampliar plazo",
                "Fiador obligatorio con calificación crediticia limpia",
                "Inspección adicional del domicilio"
            );
            return buildCondicional(score, riesgo, 0.70, conds, "Score aceptable pero riesgo alto — condiciones estrictas");
        }

        // Rechazo por combinación score-riesgo
        return buildRechazado(score, riesgo,
                "Combinación desfavorable: score " + score + " con riesgo " + riesgo.name());
    }

    // ── Builders privados ────────────────────────────────────────────────────

    private ResultadoDecision buildAprobado(double score, NivelRiesgo riesgo,
                                             double pctMonto, String justificacion) {
        return ResultadoDecision.builder()
                .decision(TipoDecision.APROBADO)
                .scoreFinal(score)
                .nivelRiesgo(riesgo.name())
                .condicionesRecomendadas(List.of())
                .justificacion(justificacion)
                .porcentajeMontoRecomendado(pctMonto)
                .build();
    }

    private ResultadoDecision buildCondicional(double score, NivelRiesgo riesgo,
                                                double pctMonto, List<String> condiciones,
                                                String justificacion) {
        return ResultadoDecision.builder()
                .decision(TipoDecision.CONDICIONAL)
                .scoreFinal(score)
                .nivelRiesgo(riesgo.name())
                .condicionesRecomendadas(new ArrayList<>(condiciones))
                .justificacion(justificacion)
                .porcentajeMontoRecomendado(pctMonto)
                .build();
    }

    private ResultadoDecision buildRechazado(double score, NivelRiesgo riesgo, String justificacion) {
        return ResultadoDecision.builder()
                .decision(TipoDecision.RECHAZADO)
                .scoreFinal(score)
                .nivelRiesgo(riesgo.name())
                .condicionesRecomendadas(List.of())
                .justificacion(justificacion)
                .porcentajeMontoRecomendado(0.0)
                .build();
    }
}
