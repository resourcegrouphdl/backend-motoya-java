package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.application.port.in.command.RegistrarPagoCommand;
import reactor.core.publisher.Mono;

public interface RegistrarPagoUseCase {
    Mono<Void> ejecutar(RegistrarPagoCommand command);
}
