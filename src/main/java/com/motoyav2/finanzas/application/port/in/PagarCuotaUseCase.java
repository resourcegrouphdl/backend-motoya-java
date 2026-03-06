package com.motoyav2.finanzas.application.port.in;

import reactor.core.publisher.Mono;

public interface PagarCuotaUseCase {
    Mono<Void> ejecutar(String cuentaId, String cuotaId);
}
