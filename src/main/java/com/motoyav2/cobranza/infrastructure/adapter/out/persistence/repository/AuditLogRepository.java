package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AuditLogDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Registro inmutable — sin update ni delete.
 * Solo ADMIN puede leer. Solo backend (Service Account) puede escribir.
 */
@Repository
public interface AuditLogRepository extends FirestoreReactiveRepository<AuditLogDocument> {

    Flux<AuditLogDocument> findByEntidadId(String entidadId);

    Flux<AuditLogDocument> findByUsuarioId(String usuarioId);
}
