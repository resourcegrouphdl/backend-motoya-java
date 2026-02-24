package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.port.in.command.RegistrarPromesaCommand;
import reactor.core.publisher.Mono;

public interface RegistrarPromesaUseCase {

    /** Registra una promesa de pago. Falla con ConflictException si ya existe una VIGENTE. */
    Mono<String> ejecutar(RegistrarPromesaCommand command);
}
