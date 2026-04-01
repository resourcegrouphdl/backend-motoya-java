package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.in.query.ListarCasosQuery;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.CasoCobranzaRepository;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
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

        boolean tieneAgente = q.agenteId() != null && !q.agenteId().isBlank();
        boolean tieneEstado = q.estado()   != null && !q.estado().isBlank();
        boolean tieneStore  = q.storeId()  != null && !q.storeId().isBlank();

        if (tieneAgente && tieneEstado) {
            base = repository.findByAgenteAsignadoIdAndEstadoCaso(q.agenteId(), q.estado());
        } else if (tieneAgente) {
            base = repository.findByAgenteAsignadoId(q.agenteId());
        } else if (tieneStore && tieneEstado) {
            base = repository.findByStoreIdAndEstadoCaso(q.storeId(), q.estado());
        } else if (tieneStore) {
            base = repository.findByStoreId(q.storeId());
        } else {
            // ADMIN sin filtro de tienda — devuelve todos
            base = repository.findAll();
        }

        // Omite documentos corruptos (@DocumentId conflict) sin terminar el stream
        return base
                .onErrorContinue(RuntimeException.class,
                        (e, obj) -> log.warn("[CasoCobranza] Documento omitido por error de deserialización: {}", e.getMessage()))
                .transformDeferred(RetryOperator.of(retryRegistry.retry("firestoreTimeout")));
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
    public Flux<CasoCobranzaDocument> findAll() {
        return repository.findAll()
                .onErrorContinue(RuntimeException.class,
                        (e, obj) -> log.warn("[CasoCobranza] Documento omitido: {}", e.getMessage()));
    }

    @Override
    public Mono<CasoCobranzaDocument> save(CasoCobranzaDocument caso) {
        return repository.save(caso);
    }
}
