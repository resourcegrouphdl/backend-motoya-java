package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.ComprobantePagoDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ComprobantePagoPort {

    Mono<ComprobantePagoDocument> save(ComprobantePagoDocument comprobante);

    Mono<ComprobantePagoDocument> findById(String id);

    Flux<ComprobantePagoDocument> findByContratoId(String contratoId);

    Flux<ComprobantePagoDocument> findByStoreId(String storeId);
}
