package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MensajeWhatsappDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface MensajeWhatsappRepository extends FirestoreReactiveRepository<MensajeWhatsappDocument> {

    Flux<MensajeWhatsappDocument> findByContratoId(String contratoId);

    /** Lookup por wamid en webhook de Twilio — campo indexado */
    Mono<MensajeWhatsappDocument> findByWamid(String wamid);
}
