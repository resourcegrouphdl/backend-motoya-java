package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.HistorialEstadoDocument;
import reactor.core.publisher.Flux;

public interface HistorialEstadoRepository extends FirestoreReactiveRepository<HistorialEstadoDocument> {
    Flux<HistorialEstadoDocument> findBySolicitudId(String solicitudId);
}
