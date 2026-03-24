package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface CorregirNombreDesdeApiUseCase {
    /**
     * Sobreescribe nombres, apellidoPaterno y apellidoMaterno del cliente
     * con los valores que devolvió la API de verificación (fuente de verdad).
     * Solo aplica si ya se hizo una verificación exitosa (exitoso = true).
     */
    Mono<Void> ejecutar(String clienteId);
}
