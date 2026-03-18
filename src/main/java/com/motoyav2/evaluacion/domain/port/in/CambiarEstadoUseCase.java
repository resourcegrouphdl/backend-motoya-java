package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.application.command.CambiarEstadoCommand;
import com.motoyav2.evaluacion.domain.model.HistorialEstado;
import reactor.core.publisher.Mono;

public interface CambiarEstadoUseCase {
    Mono<HistorialEstado> ejecutar(CambiarEstadoCommand command);
}
