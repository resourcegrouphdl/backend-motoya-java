package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PromesaDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PromesaPort {

    Mono<PromesaDocument> save(String contratoId, PromesaDocument promesa);

    Mono<PromesaDocument> findById(String contratoId, String promesaId);

    Flux<PromesaDocument> findByContratoId(String contratoId);

    /** Retorna la promesa en estado VIGENTE, o empty() si no existe ninguna. */
    Mono<PromesaDocument> findVigente(String contratoId);
}
