package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.DecisionFinalCommand;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import reactor.core.publisher.Mono;

public interface RegistrarDecisionFinalUseCase {
    Mono<Solicitud> ejecutar(DecisionFinalCommand command);
}
