package com.motoyav2.finanzas.application.port.in;

import reactor.core.publisher.Mono;

public interface PagarCuentaUseCase {
    Mono<Void> ejecutar(String cuentaId);
}
