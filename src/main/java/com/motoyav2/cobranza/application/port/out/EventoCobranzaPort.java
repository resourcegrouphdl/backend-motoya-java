package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida para la sub-colección APPEND ONLY de eventos.
 * Los eventos nunca se modifican ni eliminan.
 */
public interface EventoCobranzaPort {

    Mono<EventoCobranzaDocument> append(String contratoId, EventoCobranzaDocument evento);

    Flux<EventoCobranzaDocument> findByContratoId(String contratoId);
}
