package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.VerificarIdentidadCommand;
import com.motoyav2.evaluacion.application.dto.VerificacionIdentidadResult;
import reactor.core.publisher.Mono;

public interface VerificarIdentidadUseCase {
    Mono<VerificacionIdentidadResult> ejecutar(VerificarIdentidadCommand command);
}
