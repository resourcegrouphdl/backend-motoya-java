package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.port.in.RechazarReferenciaUseCase;
import com.motoyav2.evaluacion.domain.port.out.AlertaRepository;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RechazarReferenciaUseCaseImpl implements RechazarReferenciaUseCase {

    private final ReferenciaRepository referenciaRepository;
    private final SolicitudRepository solicitudRepository;
    private final AlertaRepository alertaRepository;

    @Override
    public Mono<Void> ejecutar(String referenciaId, String solicitudId) {
        return referenciaRepository.findById(referenciaId)
                .switchIfEmpty(Mono.error(new RecursoNoEncontradoException("Referencia no encontrada: " + referenciaId)))
                .flatMap(referencia -> {
                    Timestamp ahora = Timestamp.now();

                    // Marcar referencia como rechazada
                    Map<String, Object> refUpdates = new HashMap<>();
                    refUpdates.put("rechazada", true);
                    refUpdates.put("estadoVerificacion", "no_contactado");
                    refUpdates.put("fechaRechazo", ahora);
                    refUpdates.put("updatedAt", ahora);

                    // Crear alerta
                    Map<String, Object> alerta = new HashMap<>();
                    alerta.put("tipo", "referencia_rechazada");
                    alerta.put("solicitudId", solicitudId);
                    alerta.put("referenciaId", referenciaId);
                    alerta.put("leida", false);
                    alerta.put("prioridad", "alta");
                    alerta.put("createdAt", ahora);

                    Mono<Void> updateReferencia = referenciaRepository.updateFields(referenciaId, refUpdates);
                    Mono<Void> crearAlerta = alertaRepository.save(alerta);
                    Mono<Void> removeFromSolicitud = removeReferenciaFromSolicitud(solicitudId, referenciaId, ahora);

                    return updateReferencia
                            .then(crearAlerta)
                            .then(removeFromSolicitud);
                });
    }

    private Mono<Void> removeReferenciaFromSolicitud(String solicitudId, String referenciaId, Timestamp ahora) {
        if (solicitudId == null || solicitudId.isBlank()) return Mono.empty();
        return solicitudRepository.findById(solicitudId)
                .flatMap(solicitud -> {
                    List<String> ids = solicitud.getReferenciasIds() != null
                            ? new ArrayList<>(solicitud.getReferenciasIds())
                            : new ArrayList<>();
                    ids.remove(referenciaId);
                    Map<String, Object> updates = Map.of(
                            "referenciasIds", ids,
                            "updatedAt", ahora
                    );
                    return solicitudRepository.updateFields(solicitudId, updates);
                })
                .onErrorComplete();
    }
}
