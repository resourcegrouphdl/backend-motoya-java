package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.FalloProcesamientoDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface FalloProcesamientoRepository extends FirestoreReactiveRepository<FalloProcesamientoDocument> {

    Flux<FalloProcesamientoDocument> findByResuelta(Boolean resuelta);

    Flux<FalloProcesamientoDocument> findByFunctionName(String functionName);
}
