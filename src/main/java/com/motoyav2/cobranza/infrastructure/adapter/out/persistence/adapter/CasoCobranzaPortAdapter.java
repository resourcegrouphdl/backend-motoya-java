package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.in.query.ListarCasosQuery;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.service.IniciarCasoService;
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

        boolean esAdmin     = "ADMIN".equalsIgnoreCase(q.rol());
        boolean tieneAgente = q.agenteId() != null && !q.agenteId().isBlank();
        boolean tieneEstado = q.estado()   != null && !q.estado().isBlank();
        boolean tieneStore  = q.storeId()  != null && !q.storeId().isBlank();

        if (tieneAgente && tieneEstado) {
            base = repository.findByAgenteAsignadoIdAndEstadoCaso(q.agenteId(), q.estado());
        } else if (tieneAgente) {
            base = repository.findByAgenteAsignadoId(q.agenteId());
        } else if (esAdmin) {
            // ADMIN ve toda la cartera sin importar su storeId
            base = repository.findAll();
        } else if (tieneStore && tieneEstado) {
            String estadoFinal = q.estado();
            Flux<CasoCobranzaDocument> deEstaTienda = repository.findByStoreIdAndEstadoCaso(q.storeId(), estadoFinal);
            // Pool cobranzas indexado + legacy null para datos anteriores al centinela
            Flux<CasoCobranzaDocument> deCobranzas  = repository.findByStoreIdAndEstadoCaso(
                    IniciarCasoService.STORE_COBRANZAS, estadoFinal);
            Flux<CasoCobranzaDocument> legacy       = repository.findAll()
                    .filter(c -> (c.getStoreId() == null || c.getStoreId().isBlank())
                              && estadoFinal.equals(c.getEstadoCaso()));
            base = Flux.merge(deEstaTienda, deCobranzas, legacy);
        } else if (tieneStore) {
            Flux<CasoCobranzaDocument> deEstaTienda = repository.findByStoreId(q.storeId());
            Flux<CasoCobranzaDocument> deCobranzas  = repository.findByStoreId(IniciarCasoService.STORE_COBRANZAS);
            Flux<CasoCobranzaDocument> legacy       = repository.findAll()
                    .filter(c -> c.getStoreId() == null || c.getStoreId().isBlank());
            base = Flux.merge(deEstaTienda, deCobranzas, legacy);
        } else {
            base = repository.findAll();
        }

        // Omite documentos corruptos sin terminar el stream, pero los registra con detalle
        return base
                .onErrorContinue(RuntimeException.class,
                        (e, obj) -> log.error("[CasoCobranza] DOCUMENTO OMITIDO — obj={} tipo={} error={} causa={}",
                                obj, obj != null ? obj.getClass().getSimpleName() : "null",
                                e.getMessage(),
                                e.getCause() != null ? e.getCause().getMessage() : "sin causa"))
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
                        (e, obj) -> log.error("[CasoCobranza] DOCUMENTO OMITIDO (findAll) — obj={} tipo={} error={} causa={}",
                                obj, obj != null ? obj.getClass().getSimpleName() : "null",
                                e.getMessage(),
                                e.getCause() != null ? e.getCause().getMessage() : "sin causa"));
    }

    @Override
    public Mono<CasoCobranzaDocument> save(CasoCobranzaDocument caso) {
        return repository.save(caso);
    }
}
