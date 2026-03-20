package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.IngresarSolicitudCommand;
import reactor.core.publisher.Mono;

public interface ReemplazarFiadorUseCase {
    Mono<Void> ejecutar(String solicitudId, IngresarSolicitudCommand.ClienteData fiador);
}
