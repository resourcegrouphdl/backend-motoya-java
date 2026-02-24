package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.NumeradorDocument;
import org.springframework.stereotype.Repository;

/**
 * IMPORTANTE: todas las lecturas/escrituras de numeradores DEBEN realizarse
 * dentro de una Firestore transaction para garantizar correlativos únicos.
 * Usar ReactiveFirestoreTemplate.runTransaction() en el servicio.
 */
@Repository
public interface NumeradorRepository extends FirestoreReactiveRepository<NumeradorDocument> {
}
