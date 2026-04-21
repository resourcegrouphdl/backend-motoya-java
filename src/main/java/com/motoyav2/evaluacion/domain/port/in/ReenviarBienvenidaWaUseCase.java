package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface ReenviarBienvenidaWaUseCase {
    Mono<Void> reenviar(String solicitudId, boolean esFiador);
}
