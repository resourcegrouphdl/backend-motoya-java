package com.motoyav2.contrato.domain.port.in;

import reactor.core.publisher.Mono;

public interface EliminarContratoUseCase {
    Mono<Void> eliminar(String contratoId);
}
