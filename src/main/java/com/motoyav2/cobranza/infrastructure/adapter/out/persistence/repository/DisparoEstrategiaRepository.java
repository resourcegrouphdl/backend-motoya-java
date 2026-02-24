package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.DisparoEstrategiaDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DisparoEstrategiaRepository extends FirestoreReactiveRepository<DisparoEstrategiaDocument> {

    Flux<DisparoEstrategiaDocument> findByContratoId(String contratoId);

    Flux<DisparoEstrategiaDocument> findByEstrategiaId(String estrategiaId);
}
