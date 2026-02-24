package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.out.PlantillaWhatsappPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PlantillaWhatsappDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.PlantillaWhatsappRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PlantillaWhatsappPortAdapter implements PlantillaWhatsappPort {

    private final PlantillaWhatsappRepository repository;

    @Override
    public Mono<PlantillaWhatsappDocument> findById(String plantillaId) {
        return repository.findById(plantillaId);
    }

    @Override
    public Flux<PlantillaWhatsappDocument> findActivas() {
        return repository.findByActivaAndAprobadaPorMeta(true, true);
    }
}
