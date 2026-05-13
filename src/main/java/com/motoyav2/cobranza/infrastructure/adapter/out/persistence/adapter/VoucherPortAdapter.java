package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.out.VoucherPort;
import com.motoyav2.cobranza.application.service.IniciarCasoService;
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

    /** storeId del pool de cobranzas (clientes migrados sin tienda de origen). */
    private static final String STORE_COBRANZAS = IniciarCasoService.STORE_COBRANZAS;

    @Override
    public Flux<VoucherDocument> findByStoreIdAndEstado(String storeId, String estado) {
        boolean tieneStore  = storeId != null && !storeId.isBlank();
        boolean tieneEstado = estado  != null && !estado.isBlank();

        if (tieneStore && tieneEstado) {
            Flux<VoucherDocument> deEstaTienda  = repository.findByStoreIdAndEstado(storeId, estado);
            // Pool cobranzas: query indexada (nuevos) + filtro null (datos históricos previos al centinela)
            Flux<VoucherDocument> deCobranzas   = repository.findByStoreIdAndEstado(STORE_COBRANZAS, estado);
            Flux<VoucherDocument> legacy        = repository.findByEstado(estado)
                    .filter(v -> v.getStoreId() == null || v.getStoreId().isBlank());
            return Flux.merge(deEstaTienda, deCobranzas, legacy);
        }
        if (tieneStore) {
            Flux<VoucherDocument> deEstaTienda  = repository.findByStoreId(storeId);
            Flux<VoucherDocument> deCobranzas   = repository.findByStoreId(STORE_COBRANZAS);
            Flux<VoucherDocument> legacy        = repository.findAll()
                    .filter(v -> v.getStoreId() == null || v.getStoreId().isBlank());
            return Flux.merge(deEstaTienda, deCobranzas, legacy);
        }
        if (tieneEstado) return repository.findByEstado(estado);
        return repository.findAll();
    }

    @Override
    public Flux<VoucherDocument> findByContratoId(String contratoId) {
        return repository.findByContratoId(contratoId);
    }
}
