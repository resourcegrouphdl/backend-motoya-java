package com.motoyav2.contrato.domain.port.out;

import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.ContratoListItem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ContratoRepository {

    Mono<Contrato> findById(String id);

    Flux<ContratoListItem> findAll();

    Flux<ContratoListItem> findByTiendaId(String tiendaId);

    Mono<Contrato> save(Contrato contrato);
}
