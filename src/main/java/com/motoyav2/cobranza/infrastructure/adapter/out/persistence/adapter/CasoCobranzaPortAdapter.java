package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.in.query.ListarCasosQuery;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.CasoCobranzaRepository;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CasoCobranzaPortAdapter implements CasoCobranzaPort {

    private final CasoCobranzaRepository repository;
    private final RetryRegistry retryRegistry;

    // -------------------------------------------------------------------------
    // Query principal — filtros + Retry backoff exponencial
    // -------------------------------------------------------------------------

    @Override
    public Flux<CasoCobranzaDocument> query(ListarCasosQuery q) {
        Flux<CasoCobranzaDocument> base;

        boolean tieneAgente = q.agenteId() != null;
        boolean tieneEstado = q.estado() != null;

        if (tieneAgente && tieneEstado) {
            base = repository.findByAgenteAsignadoIdAndEstadoCaso(q.agenteId(), q.estado());
        } else if (tieneAgente) {
            base = repository.findByAgenteAsignadoId(q.agenteId());
        } else if (tieneEstado) {
            base = repository.findByStoreIdAndEstadoCaso(q.storeId(), q.estado());
        } else {
            base = repository.findByStoreId(q.storeId());
        }

        // Retry 3x con backoff exponencial (instancia "firestoreTimeout" de application.properties)
        return base.transformDeferred(RetryOperator.of(retryRegistry.retry("firestoreTimeout")));
    }

    // -------------------------------------------------------------------------
    // Métodos de soporte usados por otros servicios
    // -------------------------------------------------------------------------

    @Override
    public Mono<CasoCobranzaDocument> findById(String contratoId) {
        return repository.findById(contratoId);
    }

    @Override
    public Flux<CasoCobranzaDocument> findByStoreId(String storeId) {
        return repository.findByStoreId(storeId);
    }

    @Override
    public Flux<CasoCobranzaDocument> findByStoreIdAndNivelEstrategia(String storeId, String nivelEstrategia) {
        return repository.findByStoreIdAndNivelEstrategia(storeId, nivelEstrategia);
    }

    @Override
    public Flux<CasoCobranzaDocument> findByStoreIdAndCicloVida(String storeId, String cicloVida) {
        return repository.findByStoreIdAndCicloVida(storeId, cicloVida);
    }

    @Override
    public Flux<CasoCobranzaDocument> findByAgenteAsignadoId(String agenteId) {
        return repository.findByAgenteAsignadoId(agenteId);
    }

    @Override
    public Mono<CasoCobranzaDocument> save(CasoCobranzaDocument caso) {
        return repository.save(caso);
    }
}
