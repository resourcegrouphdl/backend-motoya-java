package com.motoyav2.evaluacion.application.service;

import com.motoyav2.evaluacion.application.port.in.TransicionarEstadoUseCase;
import com.motoyav2.evaluacion.application.port.out.CambioEstadoPort;
import com.motoyav2.evaluacion.application.port.out.SolicitudActualizacionPort;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import com.motoyav2.evaluacion.domain.service.MotorDePipeline;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.pipeline.TransicionarEstadoRequest;
import com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.pipeline.TransicionarEstadoResponse;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.FirebaseSolicitudRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransicionarEstadoService implements TransicionarEstadoUseCase {

    private final FirebaseSolicitudRepository solicitudRepository;
    private final MotorDePipeline motorDePipeline;
    private final SolicitudActualizacionPort solicitudActualizacion;
    private final CambioEstadoPort cambioEstado;

    @Override
    public Mono<TransicionarEstadoResponse> ejecutar(String solicitudId, TransicionarEstadoRequest request) {
        return solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Solicitud no encontrada: " + solicitudId)))
                .flatMap(solicitud -> {
                    EstadoSolicitud estadoActual = EstadoSolicitud.fromString(solicitud.getEstado());
                    EstadoSolicitud estadoNuevo  = EstadoSolicitud.fromString(request.getNuevoEstado());

                    try {
                        motorDePipeline.validarTransicion(estadoActual, estadoNuevo);
                    } catch (IllegalStateException e) {
                        return Mono.error(new IllegalArgumentException(e.getMessage()));
                    }

                    log.info("Transicionando solicitud {} : {} → {}",
                            solicitudId, estadoActual, estadoNuevo);

                    // Escrituras paralelas: actualizar estado + registrar en historial
                    Mono<Void> actualizarEstado = solicitudActualizacion
                            .actualizarEstado(solicitudId, estadoNuevo.name());

                    Mono<Void> registrarHistorial = cambioEstado.registrar(
                            solicitudId, estadoActual, estadoNuevo,
                            request.getUsuarioId(), request.getUsuarioNombre(), request.getMotivo());

                    return Mono.when(actualizarEstado, registrarHistorial)
                            .thenReturn(buildResponse(solicitudId, estadoActual, estadoNuevo));
                });
    }

    private TransicionarEstadoResponse buildResponse(String solicitudId,
                                                      EstadoSolicitud anterior,
                                                      EstadoSolicitud nuevo) {
        Set<String> siguientes = motorDePipeline.transicionesPosibles(nuevo)
                .stream().map(EstadoSolicitud::name).collect(Collectors.toSet());

        return TransicionarEstadoResponse.builder()
                .success(true)
                .solicitudId(solicitudId)
                .estadoAnterior(anterior.name())
                .estadoNuevo(nuevo.name())
                .mensaje("Estado actualizado correctamente: " + anterior + " → " + nuevo)
                .transicionesDisponibles(siguientes)
                .build();
    }
}
