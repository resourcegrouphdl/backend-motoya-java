package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.VerificarReferenciaCommand;
import com.motoyav2.evaluacion.domain.model.Referencia;
import reactor.core.publisher.Mono;

public interface VerificarReferenciaUseCase {
    Mono<Referencia> ejecutar(VerificarReferenciaCommand command);
}
