package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AcuerdoDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AcuerdoPort {

    Mono<AcuerdoDocument> save(String contratoId, AcuerdoDocument acuerdo);

    Flux<AcuerdoDocument> findByContratoId(String contratoId);
}
