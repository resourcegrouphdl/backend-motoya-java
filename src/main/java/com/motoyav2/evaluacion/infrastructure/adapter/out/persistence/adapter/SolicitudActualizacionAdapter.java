package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.motoyav2.evaluacion.application.port.out.SolicitudActualizacionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter que actualiza campos específicos en la colección solicitudes
 * usando el Firestore SDK directamente para actualizaciones parciales.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolicitudActualizacionAdapter implements SolicitudActualizacionPort {

    private final Firestore firestore;

    @Override
    public Mono<Void> actualizarEstado(String solicitudId, String nuevoEstado) {
        return partialUpdate(solicitudId, Map.of(
                "estado", nuevoEstado,
                "updatedAt", FieldValue.serverTimestamp()
        ));
    }

    @Override
    public Mono<Void> actualizarScores(String solicitudId,
                                        Double scoreDocumental,
                                        Double scoreGarantes,
                                        Double scoreEntrevista,
                                        Double scoreFinal) {
        Map<String, Object> updates = new HashMap<>();
        if (scoreDocumental != null) updates.put("scoreDocumental", scoreDocumental);
        if (scoreGarantes   != null) updates.put("scoreGarantes",   scoreGarantes);
        if (scoreEntrevista != null) updates.put("scoreEntrevista", scoreEntrevista);
        if (scoreFinal      != null) updates.put("scoreFinal",      scoreFinal);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        return partialUpdate(solicitudId, updates);
    }

    @Override
    public Mono<Void> actualizarEstadoYScores(String solicitudId,
                                               String nuevoEstado,
                                               Double scoreDocumental,
                                               Double scoreGarantes,
                                               Double scoreEntrevista,
                                               Double scoreFinal) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("estado", nuevoEstado);
        if (scoreDocumental != null) updates.put("scoreDocumental", scoreDocumental);
        if (scoreGarantes   != null) updates.put("scoreGarantes",   scoreGarantes);
        if (scoreEntrevista != null) updates.put("scoreEntrevista", scoreEntrevista);
        if (scoreFinal      != null) updates.put("scoreFinal",      scoreFinal);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        return partialUpdate(solicitudId, updates);
    }

    @Override
    public Mono<Void> actualizarDecisionFinal(String solicitudId,
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
                                               Double scoreFinal) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("estado",          decisionFinal);
        updates.put("decisionFinal",   decisionFinal);
        updates.put("evaluador",       evaluadorId);
        updates.put("resultadoFinal",  decisionFinal);
        updates.put("fechaDecisionFinal", FieldValue.serverTimestamp());
        updates.put("updatedAt",       FieldValue.serverTimestamp());

        if (montoAprobado     != null) updates.put("montoAprobado",          montoAprobado);
        if (motivoDecision    != null) updates.put("motivoDecision",          motivoDecision);
        if (motivoRechazo     != null) updates.put("motivoRechazo",           motivoRechazo);
        if (condicionesAprobacion != null && !condicionesAprobacion.isEmpty())
                                       updates.put("condicionesAprobacion",   condicionesAprobacion);
        if (fortalezasCaso    != null) updates.put("fortalezasCaso",          fortalezasCaso);
        if (debilidadesCaso   != null) updates.put("debilidadesCaso",         debilidadesCaso);
        if (scoreDocumental   != null) updates.put("scoreDocumental",         scoreDocumental);
        if (scoreGarantes     != null) updates.put("scoreGarantes",           scoreGarantes);
        if (scoreEntrevista   != null) updates.put("scoreEntrevista",         scoreEntrevista);
        if (scoreFinal        != null) updates.put("scoreFinal",              scoreFinal);

        return partialUpdate(solicitudId, updates);
    }

    private Mono<Void> partialUpdate(String solicitudId, Map<String, Object> fields) {
        return Mono.fromCallable(() -> {
            firestore.collection("solicitudes")
                    .document(solicitudId)
                    .update(fields)
                    .get();
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then()
        .doOnSuccess(v -> log.info("solicitudes/{} actualizado: {}", solicitudId, fields.keySet()))
        .doOnError(e -> log.error("Error actualizando solicitudes/{}: {}", solicitudId, e.getMessage()));
    }
}
