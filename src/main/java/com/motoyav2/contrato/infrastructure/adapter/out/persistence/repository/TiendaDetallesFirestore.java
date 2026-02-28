package com.motoyav2.contrato.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.document.TiendaDetalles;
import org.springframework.stereotype.Repository;

@Repository
public interface TiendaDetallesFirestore extends FirestoreReactiveRepository<TiendaDetalles> {



}
