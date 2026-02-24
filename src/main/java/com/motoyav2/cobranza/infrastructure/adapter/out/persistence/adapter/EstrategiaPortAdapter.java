package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.out.EstrategiaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EstrategiaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.EstrategiaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class EstrategiaPortAdapter implements EstrategiaPort {

    private final EstrategiaRepository repository;

    @Override
    public Flux<EstrategiaDocument> findAll() {
        return repository.findAll();
    }

    @Override
    public Mono<EstrategiaDocument> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Mono<EstrategiaDocument> save(EstrategiaDocument estrategia) {
        return repository.save(estrategia);
    }
}
