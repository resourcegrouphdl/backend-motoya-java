package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PromesaDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio para CollectionGroup queries sobre la sub-colección "promesas".
 */
@Repository
public interface PromesaRepository extends FirestoreReactiveRepository<PromesaDocument> {

    Flux<PromesaDocument> findByContratoId(String contratoId);

    /** Valida la regla: solo UNA promesa VIGENTE por caso */
    Mono<PromesaDocument> findByContratoIdAndEstado(String contratoId, String estado);

    /** Job de promesas vencidas: busca todas VIGENTE en un rango de fecha */
    Flux<PromesaDocument> findByEstado(String estado);
}
