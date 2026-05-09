package com.motoyav2.finanzas.application.port.in;

import reactor.core.publisher.Mono;

public interface BackfillClienteComisionUseCase {
    /** Rellena clienteNombre y clienteDocumento en comisiones que no los tienen. */
    Mono<Integer> ejecutar();
}
