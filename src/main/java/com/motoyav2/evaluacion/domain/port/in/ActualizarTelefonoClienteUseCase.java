package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface ActualizarTelefonoClienteUseCase {
    Mono<Void> actualizarTelefono(String clienteId, String telefono1);
}
