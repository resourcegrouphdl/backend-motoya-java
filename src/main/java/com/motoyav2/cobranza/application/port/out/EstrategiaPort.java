package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EstrategiaDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EstrategiaPort {

    Flux<EstrategiaDocument> findAll();

    Mono<EstrategiaDocument> findById(String id);

    Mono<EstrategiaDocument> save(EstrategiaDocument estrategia);
}
