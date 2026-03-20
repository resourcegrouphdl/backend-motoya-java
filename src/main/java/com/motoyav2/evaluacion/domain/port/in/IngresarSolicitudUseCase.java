package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand;
import com.motoyav2.evaluacion.application.dto.IngresarSolicitudResult;
import reactor.core.publisher.Mono;

public interface IngresarSolicitudUseCase {
    Mono<IngresarSolicitudResult> ejecutar(IngresarSolicitudCommand command);
}
