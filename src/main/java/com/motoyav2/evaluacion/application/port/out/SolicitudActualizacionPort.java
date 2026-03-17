package com.motoyav2.evaluacion.application.port.out;

import reactor.core.publisher.Mono;
import java.util.List;

/**
 * Puerto de salida para actualizaciones parciales en solicitudes.
 * Escribe estado, scores y decisión sin reemplazar el documento completo.
 */
public interface SolicitudActualizacionPort {

    /** Actualiza el estado en solicitudes/{solicitudId}. */
    Mono<Void> actualizarEstado(String solicitudId, String nuevoEstado);

    /** Actualiza los scores calculados en solicitudes/{solicitudId}. */
    Mono<Void> actualizarScores(String solicitudId,
                                 Double scoreDocumental,
                                 Double scoreGarantes,
                                 Double scoreEntrevista,
                                 Double scoreFinal);

    /** Actualiza estado + scores en una sola operación atómica. */
    Mono<Void> actualizarEstadoYScores(String solicitudId,
                                        String nuevoEstado,
                                        Double scoreDocumental,
                                        Double scoreGarantes,
                                        Double scoreEntrevista,
                                        Double scoreFinal);

    /**
     * Persiste la decisión final en solicitudes/{solicitudId}.
     * Escribe: estado, decisionFinal, montoAprobado, motivoDecision, motivoRechazo,
     * condicionesAprobacion, fortalezasCaso, debilidadesCaso, evaluador, fechaDecisionFinal,
     * y los scores finales calculados.
     */
    Mono<Void> actualizarDecisionFinal(String solicitudId,
                                        String decisionFinal,
                                        Double montoAprobado,
                                        String motivoDecision,
                                        String motivoRechazo,
                                        List<String> condicionesAprobacion,
                                        String fortalezasCaso,
                                        String debilidadesCaso,
                                        String evaluadorId,
                                        Double scoreFinalCalculado,
                                        Double scoreDocumental,
                                        Double scoreGarantes,
                                        Double scoreEntrevista,
                                        Double scoreFinal);
}
