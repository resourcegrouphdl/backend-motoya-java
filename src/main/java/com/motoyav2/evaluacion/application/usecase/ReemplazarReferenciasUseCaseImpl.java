package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.port.in.ReemplazarReferenciasUseCase;
import com.motoyav2.evaluacion.domain.port.out.ReferenciaRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReemplazarReferenciasUseCaseImpl implements ReemplazarReferenciasUseCase {

    private final SolicitudRepository solicitudRepository;
    private final ReferenciaRepository referenciaRepository;

    @Override
    public Mono<Void> ejecutar(String solicitudId, List<IngresarSolicitudCommand.ReferenciaData> referencias) {
        Timestamp ahora = Timestamp.now();

        return solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new RecursoNoEncontradoException("Solicitud no encontrada: " + solicitudId)))
                .flatMap(solicitud -> {
                    if (solicitud.getEstado() != EstadoSolicitud.REFERENCIAS_RECHAZADAS) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "La solicitud no está en estado referencias_rechazadas"));
                    }

                    AtomicInteger idx = new AtomicInteger(1);
                    Mono<List<String>> nuevasIdsMono = Flux.fromIterable(referencias)
                            .flatMap(ref -> {
                                Map<String, Object> refMap = buildReferenciaMap(
                                        ref, solicitud.getTitularId(), idx.getAndIncrement(),
                                        solicitud.getCodigoDeSolicitud(), ahora);
                                return referenciaRepository.create(refMap);
                            })
                            .collectList();

                    return nuevasIdsMono.flatMap(nuevasIds -> {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("referenciasIds", nuevasIds);
                        updates.put("estado", EstadoSolicitud.EVALUACION_GARANTES.getFirestoreValue());
                        updates.put("updatedAt", ahora);
                        log.info("Referencias reemplazadas en solicitud {} → {} nuevas referencias", solicitudId, nuevasIds.size());
                        return solicitudRepository.updateFields(solicitudId, updates);
                    });
                });
    }

    private Map<String, Object> buildReferenciaMap(IngresarSolicitudCommand.ReferenciaData d,
                                                     String titularId, int numero,
                                                     String codigo, Timestamp ahora) {
        Map<String, Object> m = new HashMap<>();
        m.put("nombre", d.nombre());
        m.put("apellidos", d.apellidos());
        m.put("telefono", d.telefono());
        m.put("parentesco", d.parentesco());
        m.put("titularId", titularId);
        m.put("numero", numero);
        m.put("codigoDeSolicitud", codigo);
        m.put("estadoVerificacion", "pendiente");
        m.put("rechazada", false);
        m.put("createdAt", ahora);
        m.put("updatedAt", ahora);
        return m;
    }
}
