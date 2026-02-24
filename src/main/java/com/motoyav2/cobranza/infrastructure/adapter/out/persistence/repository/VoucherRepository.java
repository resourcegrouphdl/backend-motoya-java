package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.VoucherDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface VoucherRepository extends FirestoreReactiveRepository<VoucherDocument> {

    Flux<VoucherDocument> findByStoreIdAndEstado(String storeId, String estado);

    Flux<VoucherDocument> findByContratoId(String contratoId);
}
