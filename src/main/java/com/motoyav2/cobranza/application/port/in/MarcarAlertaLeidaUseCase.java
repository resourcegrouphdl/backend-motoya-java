package com.motoyav2.cobranza.application.port.in;

import reactor.core.publisher.Mono;

public interface MarcarAlertaLeidaUseCase {

    Mono<Void> ejecutar(String alertaId);
}
