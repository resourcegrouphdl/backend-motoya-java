package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.CambiarEstadoCommand;
import com.motoyav2.evaluacion.application.command.EvaluarDocumentosCommand;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.port.in.CambiarEstadoUseCase;
import com.motoyav2.evaluacion.domain.port.in.EvaluarDocumentosUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EvaluarDocumentosUseCaseImpl implements EvaluarDocumentosUseCase {

    private final SolicitudRepository solicitudRepository;
    private final ClienteRepository clienteRepository;
    private final CambiarEstadoUseCase cambiarEstadoUseCase;

    @Override
    public Mono<Void> ejecutar(EvaluarDocumentosCommand command) {
        return solicitudRepository.findById(command.solicitudId())
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(command.solicitudId())))
                .flatMap(solicitud -> {
                    Timestamp ahora = Timestamp.now();

                    // ── Actualizar solicitud ───────────────────────────────
                    Map<String, Object> solUpdates = new HashMap<>();
                    boolean esFiador = command.clienteId() != null
                            && !command.clienteId().equals(solicitud.getTitularId());
                    if (command.scoreDocumental() != null) {
                        // Titular → scoreDocumental; Fiador → scoreGarantes
                        solUpdates.put(esFiador ? "scoreGarantes" : "scoreDocumental",
                                command.scoreDocumental());
                    }
                    if (command.observaciones() != null) {
                        solUpdates.put("observacionesGenerales", command.observaciones());
                    }
                    solUpdates.put("updatedAt", ahora);
                    Mono<Void> updateSolicitud = solicitudRepository.updateFields(command.solicitudId(), solUpdates);

                    // ── Persistir evaluación por documento en el cliente ───
                    Mono<Void> updateCliente = Mono.empty();
                    // Usa clienteId explícito si se proporcionó (caso fiador); si no, usa titularId
                    String targetClienteId = command.clienteId() != null
                            ? command.clienteId()
                            : solicitud.getTitularId();
                    if (command.evaluacionDocumentos() != null
                            && !command.evaluacionDocumentos().isEmpty()
                            && targetClienteId != null) {

                        Map<String, Object> evalMap = new HashMap<>();
                        command.evaluacionDocumentos().forEach((key, data) -> {
                            Map<String, Object> item = new HashMap<>();
                            item.put("estado", data.estado());
                            item.put("observaciones", data.observaciones() != null ? data.observaciones() : "");
                            item.put("fechaEvaluacion", ahora);
                            item.put("evaluador", command.usuarioNombre());
                            evalMap.put("evaluacionDocumentos." + key, item);
                        });
                        evalMap.put("updatedAt", ahora);
                        updateCliente = clienteRepository.updateFields(targetClienteId, evalMap);
                    }

                    Mono<Void> allUpdates = updateSolicitud.then(updateCliente);

                    if (command.nuevoEstado() != null
                            && com.motoyav2.evaluacion.domain.service.EstadoSolicitudStateMachine
                                    .esTransicionValida(solicitud.getEstado(), command.nuevoEstado())) {
                        CambiarEstadoCommand estadoCmd = new CambiarEstadoCommand(
                                command.solicitudId(),
                                command.nuevoEstado(),
                                command.usuarioId(),
                                command.usuarioNombre(),
                                "Evaluación documental — score: " + command.scoreDocumental()
                        );
                        return allUpdates.then(cambiarEstadoUseCase.ejecutar(estadoCmd)).then();
                    }
                    return allUpdates;
                });
    }
}
