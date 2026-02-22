package com.motoyav2.contrato.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.document.ContratoDocument;

public interface FirestoreContratoRepository extends FirestoreReactiveRepository<ContratoDocument> {
}
