package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.PlantillaWhatsappDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface PlantillaWhatsappRepository extends FirestoreReactiveRepository<PlantillaWhatsappDocument> {

    Flux<PlantillaWhatsappDocument> findByActivaAndAprobadaPorMeta(Boolean activa, Boolean aprobadaPorMeta);

    Flux<PlantillaWhatsappDocument> findByCategoria(String categoria);

    Flux<PlantillaWhatsappDocument> findByNivelMora(String nivelMora);
}
