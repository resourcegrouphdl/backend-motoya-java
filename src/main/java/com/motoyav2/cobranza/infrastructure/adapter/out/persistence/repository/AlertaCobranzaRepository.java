package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AlertaCobranzaDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface AlertaCobranzaRepository extends FirestoreReactiveRepository<AlertaCobranzaDocument> {

    /** Lista de alertas priorizadas por tienda (no descartadas) */
    Flux<AlertaCobranzaDocument> findByStoreIdAndDescartada(String storeId, Boolean descartada);

    /** Alertas de un agente específico no descartadas */
    Flux<AlertaCobranzaDocument> findByAgenteIdAndDescartada(String agenteId, Boolean descartada);

    Flux<AlertaCobranzaDocument> findByContratoId(String contratoId);
}
