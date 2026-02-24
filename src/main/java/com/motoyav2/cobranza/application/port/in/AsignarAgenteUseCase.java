package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.port.in.command.AsignarAgenteCommand;
import reactor.core.publisher.Mono;

public interface AsignarAgenteUseCase {

    Mono<Void> ejecutar(AsignarAgenteCommand command);
}
