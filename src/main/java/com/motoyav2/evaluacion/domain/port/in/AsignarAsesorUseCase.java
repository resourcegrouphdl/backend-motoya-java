package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.AsignarAsesorCommand;
import com.motoyav2.evaluacion.domain.model.Solicitud;
import reactor.core.publisher.Mono;

public interface AsignarAsesorUseCase {
    Mono<Solicitud> ejecutar(AsignarAsesorCommand command);
}
