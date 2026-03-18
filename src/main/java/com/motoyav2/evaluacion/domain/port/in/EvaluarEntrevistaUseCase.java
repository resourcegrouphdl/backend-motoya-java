package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.EvaluarEntrevistaCommand;
import reactor.core.publisher.Mono;

public interface EvaluarEntrevistaUseCase {
    Mono<Void> ejecutar(EvaluarEntrevistaCommand command);
}
