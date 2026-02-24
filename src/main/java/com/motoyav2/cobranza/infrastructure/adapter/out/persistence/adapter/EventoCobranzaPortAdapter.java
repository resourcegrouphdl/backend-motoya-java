package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.spring.data.firestore.FirestoreTemplate;
import com.motoyav2.cobranza.application.port.out.EventoCobranzaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Usa FirestoreTemplate.withParent() para escribir/leer la sub-colección
 * casos_cobranza/{contratoId}/eventos — APPEND ONLY.
 */
@Component
@RequiredArgsConstructor
public class EventoCobranzaPortAdapter implements EventoCobranzaPort {

    private final FirestoreTemplate firestoreTemplate;

    @Override
    public Mono<EventoCobranzaDocument> append(String contratoId, EventoCobranzaDocument evento) {
        return firestoreTemplate
                .withParent(contratoId, CasoCobranzaDocument.class)
                .save(evento);
    }

    @Override
    public Flux<EventoCobranzaDocument> findByContratoId(String contratoId) {
        return firestoreTemplate
                .withParent(contratoId, CasoCobranzaDocument.class)
                .findAll(EventoCobranzaDocument.class);
    }
}
