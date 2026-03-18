package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.application.command.AsignarAsesorCommand;
import com.motoyav2.evaluacion.application.command.CambiarEstadoCommand;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.exception.ExpedienteNotFoundException;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import com.motoyav2.evaluacion.domain.port.in.AsignarAsesorUseCase;
import com.motoyav2.evaluacion.domain.port.in.CambiarEstadoUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AsignarAsesorUseCaseImpl implements AsignarAsesorUseCase {

    private final SolicitudRepository solicitudRepository;
    private final CambiarEstadoUseCase cambiarEstadoUseCase;

    @Override
    public Mono<Solicitud> ejecutar(AsignarAsesorCommand command) {
        return solicitudRepository.findById(command.solicitudId())
                .switchIfEmpty(Mono.error(new ExpedienteNotFoundException(command.solicitudId())))
                .flatMap(solicitud -> {
                    Timestamp ahora = Timestamp.now();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("asesorAsignadoId", command.asesorId());
                    updates.put("fechaAsignacion", ahora);
                    updates.put("updatedAt", ahora);

                    return solicitudRepository.updateFields(command.solicitudId(), updates)
                            .then(autoTransicionarSiAplica(solicitud, command))
                            .then(solicitudRepository.findById(command.solicitudId()));
                });
    }

    private Mono<Void> autoTransicionarSiAplica(Solicitud solicitud, AsignarAsesorCommand command) {
        if (solicitud.getEstado() == EstadoSolicitud.PENDIENTE) {
            CambiarEstadoCommand estadoCmd = new CambiarEstadoCommand(
                    command.solicitudId(),
                    EstadoSolicitud.EN_REVISION_INICIAL,
                    command.usuarioId(),
                    command.usuarioNombre(),
                    "Asesor asignado: " + command.asesorNombre()
            );
            return cambiarEstadoUseCase.ejecutar(estadoCmd).then();
        }
        return Mono.empty();
    }
}
