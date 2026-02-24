package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.port.in.command.RechazarVoucherCommand;
import reactor.core.publisher.Mono;

public interface RechazarVoucherUseCase {

    Mono<Void> ejecutar(RechazarVoucherCommand command);
}
