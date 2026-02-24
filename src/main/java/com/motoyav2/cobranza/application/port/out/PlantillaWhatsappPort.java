package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PlantillaWhatsappDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlantillaWhatsappPort {

    Mono<PlantillaWhatsappDocument> findById(String plantillaId);

    Flux<PlantillaWhatsappDocument> findActivas();
}
