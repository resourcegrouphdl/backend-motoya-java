package com.motoyav2.contrato.domain.port.out;

import reactor.core.publisher.Mono;

public interface ContratoNumberGenerator {

    Mono<String> generate(String evaluacionId);
}
