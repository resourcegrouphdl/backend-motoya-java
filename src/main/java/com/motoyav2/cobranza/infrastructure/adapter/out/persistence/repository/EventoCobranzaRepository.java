package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EventoCobranzaDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Repositorio para CollectionGroup queries sobre la sub-colección "eventos".
 * Para escribir en una sub-colección específica usar FirestoreTemplate.withParent().
 */
@Repository
public interface EventoCobranzaRepository extends FirestoreReactiveRepository<EventoCobranzaDocument> {

    /** CollectionGroup: todos los eventos de un contrato ordenados por fecha */
    Flux<EventoCobranzaDocument> findByContratoId(String contratoId);

    Flux<EventoCobranzaDocument> findByContratoIdAndTipo(String contratoId, String tipo);
}
