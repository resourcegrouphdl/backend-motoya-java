package com.motoyav2.evaluacion.application.port.out;

import com.motoyav2.evaluacion.domain.model.Persona;
import reactor.core.publisher.Mono;

public interface ClientePort {

    Mono<Persona> buscarPorId(String clienteId);
}
