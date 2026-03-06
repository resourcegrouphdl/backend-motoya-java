package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.application.port.in.command.CrearCuentaCommand;
import com.motoyav2.finanzas.domain.model.CuentaPorPagar;
import reactor.core.publisher.Mono;

public interface CrearCuentaUseCase {
    Mono<CuentaPorPagar> ejecutar(CrearCuentaCommand command);
}
