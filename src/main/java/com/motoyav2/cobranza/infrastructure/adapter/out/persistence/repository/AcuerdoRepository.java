package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AcuerdoDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Repositorio para CollectionGroup queries sobre la sub-colección "acuerdos".
 */
@Repository
public interface AcuerdoRepository extends FirestoreReactiveRepository<AcuerdoDocument> {

    Flux<AcuerdoDocument> findByContratoId(String contratoId);

    Flux<AcuerdoDocument> findByContratoIdAndEstado(String contratoId, String estado);
}
