package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.out.MetricasPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MetricasDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.MetricasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MetricasPortAdapter implements MetricasPort {

    private final MetricasRepository repository;

    @Override
    public Mono<MetricasDocument> findResumenActual() {
        return repository.findResumenActual();
    }
}
