package com.motoyav2.riesgointerno.domain.port.in;

import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;
import reactor.core.publisher.Mono;

public interface ObtenerRegistroUseCase {
    Mono<RegistroRiesgo> obtener(String id);
}
