package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.spring.data.firestore.FirestoreTemplate;
import com.motoyav2.cobranza.application.port.out.AcuerdoPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AcuerdoDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Sub-colección casos_cobranza/{contratoId}/acuerdos
 */
@Component
@RequiredArgsConstructor
public class AcuerdoPortAdapter implements AcuerdoPort {

    private final FirestoreTemplate firestoreTemplate;

    @Override
    public Mono<AcuerdoDocument> save(String contratoId, AcuerdoDocument acuerdo) {
        return firestoreTemplate
                .withParent(contratoId, CasoCobranzaDocument.class)
                .save(acuerdo);
    }

    @Override
    public Flux<AcuerdoDocument> findByContratoId(String contratoId) {
        return firestoreTemplate
                .withParent(contratoId, CasoCobranzaDocument.class)
                .findAll(AcuerdoDocument.class);
    }
}
