package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EstrategiaDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface EstrategiaRepository extends FirestoreReactiveRepository<EstrategiaDocument> {

    /** Job: obtiene estrategias activas por nivel ordenadas por prioridad */
    Flux<EstrategiaDocument> findByActivoAndNivel(Boolean activo, String nivel);

    Flux<EstrategiaDocument> findByActivo(Boolean activo);
}
