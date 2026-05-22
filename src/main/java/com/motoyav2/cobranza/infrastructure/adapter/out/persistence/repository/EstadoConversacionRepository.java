package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EstadoConversacionDocument;
import org.springframework.stereotype.Repository;

@Repository
public interface EstadoConversacionRepository extends FirestoreReactiveRepository<EstadoConversacionDocument> {
    // findById(telefono9) desde la interfaz base — O(1)
}
