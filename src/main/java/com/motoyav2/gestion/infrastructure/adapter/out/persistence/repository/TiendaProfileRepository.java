package com.motoyav2.gestion.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.gestion.infrastructure.adapter.out.persistence.document.TiendaProfileDocument;
import reactor.core.publisher.Flux;

public interface TiendaProfileRepository
        extends FirestoreReactiveRepository<TiendaProfileDocument> {

    Flux<TiendaProfileDocument> findByTiendaStatus(String tiendaStatus);

    Flux<TiendaProfileDocument> findByIsActive(Boolean isActive);
}
