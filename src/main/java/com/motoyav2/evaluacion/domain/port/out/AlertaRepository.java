package com.motoyav2.evaluacion.domain.port.out;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface AlertaRepository {
    Mono<Void> save(Map<String, Object> alerta);
}
