package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MovimientoDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Repositorio para CollectionGroup queries sobre la sub-colección "movimientos".
 * Para escribir usar FirestoreTemplate.withParent().
 */
@Repository
public interface MovimientoRepository extends FirestoreReactiveRepository<MovimientoDocument> {

    Flux<MovimientoDocument> findByContratoId(String contratoId);

    Flux<MovimientoDocument> findByContratoIdAndTipo(String contratoId, String tipo);
}
