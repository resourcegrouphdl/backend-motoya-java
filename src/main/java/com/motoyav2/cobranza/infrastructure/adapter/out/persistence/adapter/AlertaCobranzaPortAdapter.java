package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.cobranza.application.port.out.AlertaCobranzaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.AlertaCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.repository.AlertaCobranzaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AlertaCobranzaPortAdapter implements AlertaCobranzaPort {

    private final AlertaCobranzaRepository repository;

    @Override
    public Mono<AlertaCobranzaDocument> save(AlertaCobranzaDocument alerta) {
        return repository.save(alerta);
    }

    @Override
    public Mono<AlertaCobranzaDocument> findById(String alertaId) {
        return repository.findById(alertaId);
    }

    @Override
    public Flux<AlertaCobranzaDocument> findByStoreId(String storeId) {
        return repository.findByStoreIdAndDescartada(storeId, false);
    }

    @Override
    public Flux<AlertaCobranzaDocument> findByAgenteId(String agenteId) {
        return repository.findByAgenteIdAndDescartada(agenteId, false);
    }

    @Override
    public Flux<AlertaCobranzaDocument> findByContratoId(String contratoId) {
        return repository.findByContratoId(contratoId);
    }
}
