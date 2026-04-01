package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.application.port.in.command.ConfirmarPagoComisionCommand;
import reactor.core.publisher.Mono;

public interface ConfirmarPagoComisionUseCase {
    Mono<Void> ejecutar(ConfirmarPagoComisionCommand command);
}
