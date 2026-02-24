package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.spring.data.firestore.FirestoreTemplate;
import com.motoyav2.cobranza.application.port.out.PromesaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PromesaDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Sub-colección casos_cobranza/{contratoId}/promesas
 */
@Component
@RequiredArgsConstructor
public class PromesaPortAdapter implements PromesaPort {

    private final FirestoreTemplate firestoreTemplate;

    @Override
    public Mono<PromesaDocument> save(String contratoId, PromesaDocument promesa) {
        return firestoreTemplate
                .withParent(contratoId, CasoCobranzaDocument.class)
                .save(promesa);
    }

    @Override
    public Mono<PromesaDocument> findById(String contratoId, String promesaId) {
        return firestoreTemplate
                .withParent(contratoId, CasoCobranzaDocument.class)
                .findById(Mono.just(promesaId), PromesaDocument.class);
    }

    @Override
    public Flux<PromesaDocument> findByContratoId(String contratoId) {
        return firestoreTemplate
                .withParent(contratoId, CasoCobranzaDocument.class)
                .findAll(PromesaDocument.class);
    }

    @Override
    public Mono<PromesaDocument> findVigente(String contratoId) {
        return findByContratoId(contratoId)
                .filter(p -> "VIGENTE".equals(p.getEstado()))
                .next();
    }
}
