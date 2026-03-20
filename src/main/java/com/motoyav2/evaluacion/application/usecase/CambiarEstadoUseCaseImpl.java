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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CambiarEstadoUseCaseImpl implements CambiarEstadoUseCase {

    /** Estados donde el motivo se persiste como motivoRechazo en el documento principal */
    private static final Set<EstadoSolicitud> ESTADOS_RECHAZO = Set.of(
            EstadoSolicitud.RECHAZADO,
            EstadoSolicitud.CANCELADO,
            EstadoSolicitud.CLIENTE_RECHAZADO,
            EstadoSolicitud.FIADOR_RECHAZADO,
            EstadoSolicitud.REFERENCIAS_RECHAZADAS,
            EstadoSolicitud.VEHICULO_RECHAZADO,
            EstadoSolicitud.DATOS_NO_VERIFICADOS,
            EstadoSolicitud.DOCUMENTOS_OBSERVADOS,
            EstadoSolicitud.DOCUMENTOS_INCOMPLETOS
    );

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

                    // Persistir motivoRechazo en el documento cuando la transición es a un estado de rechazo
                    if (ESTADOS_RECHAZO.contains(command.nuevoEstado())
                            && command.motivo() != null && !command.motivo().isBlank()) {
                        updates.put("motivoRechazo", command.motivo());
                    }

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
