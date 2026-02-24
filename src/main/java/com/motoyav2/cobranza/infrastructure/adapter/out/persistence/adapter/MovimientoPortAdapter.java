package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.spring.data.firestore.FirestoreTemplate;
import com.motoyav2.cobranza.application.port.out.MovimientoPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MovimientoDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Sub-colección casos_cobranza/{contratoId}/movimientos — APPEND ONLY.
 */
@Component
@RequiredArgsConstructor
public class MovimientoPortAdapter implements MovimientoPort {

    private final FirestoreTemplate firestoreTemplate;

    @Override
    public Mono<MovimientoDocument> append(String contratoId, MovimientoDocument movimiento) {
        return firestoreTemplate
                .withParent(contratoId, CasoCobranzaDocument.class)
                .save(movimiento);
    }

    @Override
    public Flux<MovimientoDocument> findByContratoId(String contratoId) {
        return firestoreTemplate
                .withParent(contratoId, CasoCobranzaDocument.class)
                .findAll(MovimientoDocument.class);
    }
}
