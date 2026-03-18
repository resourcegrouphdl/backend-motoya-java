package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface RechazarReferenciaUseCase {
    Mono<Void> ejecutar(String referenciaId, String solicitudId);
}
