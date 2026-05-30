package com.motoyav2.riesgointerno.domain.port.in;

import reactor.core.publisher.Mono;

public interface EliminarRegistroUseCase {
    Mono<Void> eliminar(String id);
}
