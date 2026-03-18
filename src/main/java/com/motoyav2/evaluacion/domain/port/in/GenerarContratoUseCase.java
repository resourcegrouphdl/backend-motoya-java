package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.GenerarContratoCommand;
import reactor.core.publisher.Mono;

public interface GenerarContratoUseCase {
    Mono<String> ejecutar(GenerarContratoCommand command);
}
