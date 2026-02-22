package com.motoyav2.contrato.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.ContratoListItem;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.mapper.ContratoDocumentMapper;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.repository.FirestoreContratoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ContratoRepositoryAdapter implements ContratoRepository {

    private final FirestoreContratoRepository firestoreRepository;

    @Override
    public Mono<Contrato> findById(String id) {
        return firestoreRepository.findById(id)
                .map(ContratoDocumentMapper::toDomain);
    }

    @Override
    public Flux<ContratoListItem> findAll() {
        return firestoreRepository.findAll()
                .map(ContratoDocumentMapper::toListItem);
    }

    @Override
    public Flux<ContratoListItem> findByTiendaId(String tiendaId) {
        return firestoreRepository.findAll()
                .filter(doc -> doc.getTienda() != null && tiendaId.equals(doc.getTienda().getTiendaId()))
                .map(ContratoDocumentMapper::toListItem);
    }

    @Override
    public Mono<Contrato> save(Contrato contrato) {
        return firestoreRepository.save(ContratoDocumentMapper.toDocument(contrato))
                .map(ContratoDocumentMapper::toDomain);
    }
}
