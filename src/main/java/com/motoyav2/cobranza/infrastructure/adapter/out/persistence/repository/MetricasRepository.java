package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MetricasDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface MetricasRepository extends FirestoreReactiveRepository<MetricasDocument> {

    /** Documento único — ID fijo "resumen_actual" */
    default Mono<MetricasDocument> findResumenActual() {
        return findById("resumen_actual");
    }
}
