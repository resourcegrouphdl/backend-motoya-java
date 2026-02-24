package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface CasoCobranzaRepository extends FirestoreReactiveRepository<CasoCobranzaDocument> {

    Flux<CasoCobranzaDocument> findByStoreId(String storeId);

    Flux<CasoCobranzaDocument> findByStoreIdAndEstadoCaso(String storeId, String estadoCaso);

    Flux<CasoCobranzaDocument> findByStoreIdAndNivelEstrategia(String storeId, String nivelEstrategia);

    Flux<CasoCobranzaDocument> findByStoreIdAndCicloVida(String storeId, String cicloVida);

    Flux<CasoCobranzaDocument> findByAgenteAsignadoId(String agenteAsignadoId);

    Flux<CasoCobranzaDocument> findByAgenteAsignadoIdAndEstadoCaso(String agenteAsignadoId, String estadoCaso);
}
