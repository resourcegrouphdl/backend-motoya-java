package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface ActualizarDireccionClienteUseCase {
    Mono<Void> actualizarDireccion(String clienteId, String direccion,
                                    String distrito, String provincia, String departamento);
}
