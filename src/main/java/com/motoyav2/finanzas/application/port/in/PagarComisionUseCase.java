package com.motoyav2.finanzas.application.port.in;

import reactor.core.publisher.Mono;

public interface PagarComisionUseCase {
    Mono<Void> ejecutar(String comisionId);
}
