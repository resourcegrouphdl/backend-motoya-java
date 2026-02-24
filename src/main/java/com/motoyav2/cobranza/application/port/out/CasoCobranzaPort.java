package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.application.port.in.query.ListarCasosQuery;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CasoCobranzaPort {

    Mono<CasoCobranzaDocument> findById(String contratoId);

    /**
     * Query principal con filtros — aplica Retry backoff exponencial.
     * La ordenación final y el cálculo de diasMora/prioridad se hacen en el servicio.
     */
    Flux<CasoCobranzaDocument> query(ListarCasosQuery q);

    Flux<CasoCobranzaDocument> findByStoreId(String storeId);

    Flux<CasoCobranzaDocument> findByStoreIdAndNivelEstrategia(String storeId, String nivelEstrategia);

    Flux<CasoCobranzaDocument> findByStoreIdAndCicloVida(String storeId, String cicloVida);

    Flux<CasoCobranzaDocument> findByAgenteAsignadoId(String agenteId);

    Mono<CasoCobranzaDocument> save(CasoCobranzaDocument caso);
}
