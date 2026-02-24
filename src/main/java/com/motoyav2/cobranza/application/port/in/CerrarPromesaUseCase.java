package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.port.in.command.CerrarPromesaCommand;
import reactor.core.publisher.Mono;

public interface CerrarPromesaUseCase {

    Mono<Void> ejecutar(CerrarPromesaCommand command);
}
