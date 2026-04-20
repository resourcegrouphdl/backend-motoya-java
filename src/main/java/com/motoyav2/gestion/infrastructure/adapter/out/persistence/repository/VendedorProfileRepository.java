package com.motoyav2.gestion.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.gestion.infrastructure.adapter.out.persistence.document.VendedorProfileDocument;
import reactor.core.publisher.Flux;

public interface VendedorProfileRepository
        extends FirestoreReactiveRepository<VendedorProfileDocument> {

    Flux<VendedorProfileDocument> findByTiendaId(String tiendaId);

    Flux<VendedorProfileDocument> findByVendedorStatus(String vendedorStatus);
}
