package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.VoucherDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VoucherPort {

    Mono<VoucherDocument> save(VoucherDocument voucher);

    Mono<VoucherDocument> findById(String voucherId);

    Flux<VoucherDocument> findByStoreIdAndEstado(String storeId, String estado);

    Flux<VoucherDocument> findByContratoId(String contratoId);
}
