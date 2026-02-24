package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.out.VoucherPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.VoucherDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class VoucherPortAdapter implements VoucherPort {

    private final VoucherRepository repository;

    @Override
    public Mono<VoucherDocument> save(VoucherDocument voucher) {
        return repository.save(voucher);
    }

    @Override
    public Mono<VoucherDocument> findById(String voucherId) {
        return repository.findById(voucherId);
    }

    @Override
    public Flux<VoucherDocument> findByStoreIdAndEstado(String storeId, String estado) {
        return repository.findByStoreIdAndEstado(storeId, estado);
    }

    @Override
    public Flux<VoucherDocument> findByContratoId(String contratoId) {
        return repository.findByContratoId(contratoId);
    }
}
