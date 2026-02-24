package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MetricasDocument;
import reactor.core.publisher.Mono;

public interface MetricasPort {

    Mono<MetricasDocument> findResumenActual();
}
