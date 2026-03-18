package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.CambiarEstadoCommand;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.model.HistorialEstado;
import com.motoyav2.evaluacion.domain.port.in.CambiarEstadoUseCase;
import com.motoyav2.evaluacion.domain.port.out.HistorialEstadoRepository;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.evaluacion.domain.service.EstadoSolicitudStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CambiarEstadoUseCaseImpl implements CambiarEstadoUseCase {

    private final SolicitudRepository solicitudRepository;
    private final HistorialEstadoRepository historialEstadoRepository;

    @Override
    public Mono<HistorialEstado> ejecutar(CambiarEstadoCommand command) {
        return solicitudRepository.findById(command.solicitudId())
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(command.solicitudId())))
                .flatMap(solicitud -> {
                    EstadoSolicitudStateMachine.validar(solicitud.getEstado(), command.nuevoEstado());

                    Timestamp ahora = Timestamp.now();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("estado", command.nuevoEstado().getFirestoreValue());
                    updates.put("updatedAt", ahora);

                    HistorialEstado historial = HistorialEstado.builder()
                            .solicitudId(command.solicitudId())
                            .estadoAnterior(solicitud.getEstado())
                            .estadoNuevo(command.nuevoEstado())
                            .fechaCambio(ahora)
                            .usuarioId(command.usuarioId())
                            .usuarioNombre(command.usuarioNombre())
                            .motivo(command.motivo())
                            .build();

                    return solicitudRepository.updateFields(command.solicitudId(), updates)
                            .then(historialEstadoRepository.save(historial))
                            .thenReturn(historial);
                });
    }
}
