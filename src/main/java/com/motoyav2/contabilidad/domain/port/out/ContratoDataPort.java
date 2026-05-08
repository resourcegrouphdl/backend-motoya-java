package com.motoyav2.contabilidad.domain.port.out;

import com.motoyav2.contabilidad.domain.model.ContratoData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ContratoDataPort {
    Flux<ContratoData> findTodos();
    Mono<ContratoData> findById(String contratoId);
}
