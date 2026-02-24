package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MensajeWhatsappDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MensajeWhatsappPort {

    Mono<MensajeWhatsappDocument> save(MensajeWhatsappDocument mensaje);

    Mono<MensajeWhatsappDocument> findByWamid(String wamid);

    Flux<MensajeWhatsappDocument> findByContratoId(String contratoId);
}
