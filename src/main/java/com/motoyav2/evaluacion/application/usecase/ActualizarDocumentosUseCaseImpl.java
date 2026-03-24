package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.port.in.ActualizarDocumentosUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActualizarDocumentosUseCaseImpl implements ActualizarDocumentosUseCase {

    private static final Set<EstadoSolicitud> ESTADOS_PERMITIDOS = Set.of(
            EstadoSolicitud.DOCUMENTOS_OBSERVADOS,
            EstadoSolicitud.DOCUMENTOS_INCOMPLETOS
    );

    private final SolicitudRepository solicitudRepository;
    private final ClienteRepository clienteRepository;

    @Override
    public Mono<Void> ejecutar(String solicitudId, Map<String, String> archivos, String clienteId) {
        Timestamp ahora = Timestamp.now();

        return solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new RecursoNoEncontradoException("Solicitud no encontrada: " + solicitudId)))
                .flatMap(solicitud -> {
                    if (!ESTADOS_PERMITIDOS.contains(solicitud.getEstado())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "La solicitud debe estar en documentos_observados o documentos_incompletos"));
                    }
                    if (solicitud.getTitularId() == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                "La solicitud no tiene titular asociado"));
                    }

                    // Usar el clienteId proporcionado o caer al titularId
                    String targetClienteId = (clienteId != null && !clienteId.isBlank())
                            ? clienteId
                            : solicitud.getTitularId();

                    // Merge parcial con dot-notation: solo sobreescribe las claves que vienen,
                    // preserva todos los archivos previos que no se re-suben.
                    Map<String, Object> clienteUpdates = new java.util.HashMap<>();
                    archivos.forEach((key, url) -> clienteUpdates.put("archivos." + key, url));
                    clienteUpdates.put("updatedAt", ahora);

                    // Transiciona automáticamente a evaluacion_documental para que el admin
                    // sepa que hay material nuevo listo para revisar.
                    Map<String, Object> solUpdates = Map.of(
                            "estado", EstadoSolicitud.EVALUACION_DOCUMENTAL.getFirestoreValue(),
                            "updatedAt", ahora
                    );

                    return clienteRepository.updateFields(targetClienteId, clienteUpdates)
                            .then(solicitudRepository.updateFields(solicitudId, solUpdates))
                            .doOnSuccess(v -> log.info(
                                    "Documentos actualizados — solicitud={} cliente={} archivos={} → evaluacion_documental",
                                    solicitudId, targetClienteId, archivos.keySet()));
                });
    }
}
