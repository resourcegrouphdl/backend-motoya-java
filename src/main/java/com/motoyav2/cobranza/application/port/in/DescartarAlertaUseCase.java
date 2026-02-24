package com.motoyav2.cobranza.application.port.in;

import reactor.core.publisher.Mono;

public interface DescartarAlertaUseCase {

    Mono<Void> ejecutar(String alertaId);
}
