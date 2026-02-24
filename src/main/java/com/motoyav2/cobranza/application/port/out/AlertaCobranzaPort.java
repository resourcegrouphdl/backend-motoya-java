package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AlertaCobranzaDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AlertaCobranzaPort {

    Mono<AlertaCobranzaDocument> save(AlertaCobranzaDocument alerta);

    Mono<AlertaCobranzaDocument> findById(String alertaId);

    Flux<AlertaCobranzaDocument> findByStoreId(String storeId);

    Flux<AlertaCobranzaDocument> findByAgenteId(String agenteId);

    Flux<AlertaCobranzaDocument> findByContratoId(String contratoId);
}
