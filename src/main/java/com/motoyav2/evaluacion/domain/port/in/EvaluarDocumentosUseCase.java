package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.EvaluarDocumentosCommand;
import reactor.core.publisher.Mono;

public interface EvaluarDocumentosUseCase {
    Mono<Void> ejecutar(EvaluarDocumentosCommand command);
}
