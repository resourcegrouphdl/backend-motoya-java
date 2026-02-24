package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.out.ComprobantePagoPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.ComprobantePagoDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.ComprobantePagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ComprobantePagoPortAdapter implements ComprobantePagoPort {

    private final ComprobantePagoRepository repository;

    @Override
    public Mono<ComprobantePagoDocument> save(ComprobantePagoDocument comprobante) {
        return repository.save(comprobante);
    }

    @Override
    public Mono<ComprobantePagoDocument> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Flux<ComprobantePagoDocument> findByContratoId(String contratoId) {
        return repository.findByContratoId(contratoId);
    }

    @Override
    public Flux<ComprobantePagoDocument> findByStoreId(String storeId) {
        return repository.findByStoreId(storeId);
    }
}
