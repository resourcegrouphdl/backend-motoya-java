package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.ComprobantePagoDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ComprobantePagoRepository extends FirestoreReactiveRepository<ComprobantePagoDocument> {

    Flux<ComprobantePagoDocument> findByContratoId(String contratoId);

    Flux<ComprobantePagoDocument> findByStoreId(String storeId);

    Flux<ComprobantePagoDocument> findByStoreIdAndTipoAndEstado(String storeId, String tipo, String estado);

    Mono<ComprobantePagoDocument> findByVoucherId(String voucherId);

    /** Busca por serie para generar correlativo — usar junto con NumeradorDocument en transaction */
    Flux<ComprobantePagoDocument> findBySerie(String serie);
}
