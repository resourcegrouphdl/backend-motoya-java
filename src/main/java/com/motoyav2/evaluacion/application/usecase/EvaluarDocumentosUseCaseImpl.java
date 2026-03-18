package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.CambiarEstadoCommand;
import com.motoyav2.evaluacion.application.command.EvaluarDocumentosCommand;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.port.in.CambiarEstadoUseCase;
import com.motoyav2.evaluacion.domain.port.in.EvaluarDocumentosUseCase;
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
    private final CambiarEstadoUseCase cambiarEstadoUseCase;

    @Override
    public Mono<Void> ejecutar(EvaluarDocumentosCommand command) {
        return solicitudRepository.findById(command.solicitudId())
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(command.solicitudId())))
                .flatMap(solicitud -> {
                    Timestamp ahora = Timestamp.now();
                    Map<String, Object> updates = new HashMap<>();
                    if (command.scoreDocumental() != null) {
                        updates.put("scoreDocumental", command.scoreDocumental());
                    }
                    if (command.observaciones() != null) {
                        updates.put("observacionesGenerales", command.observaciones());
                    }
                    updates.put("updatedAt", ahora);

                    Mono<Void> updateMono = solicitudRepository.updateFields(command.solicitudId(), updates);

                    if (command.nuevoEstado() != null) {
                        CambiarEstadoCommand estadoCmd = new CambiarEstadoCommand(
                                command.solicitudId(),
                                command.nuevoEstado(),
                                command.usuarioId(),
                                command.usuarioNombre(),
                                "Evaluación documental — score: " + command.scoreDocumental()
                        );
                        return updateMono.then(cambiarEstadoUseCase.ejecutar(estadoCmd)).then();
                    }
                    return updateMono;
                });
    }
}
