package com.motoyav2.auth.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.auth.domain.model.Store;
import com.motoyav2.auth.domain.port.out.StoreRepository;
import com.motoyav2.auth.infrastructure.adapter.out.persistence.mapper.StoreDocumentMapper;
import com.motoyav2.auth.infrastructure.adapter.out.persistence.repository.FirestoreStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StoreRepositoryAdapter implements StoreRepository {

    private final FirestoreStoreRepository firestoreStoreRepository;

    @Override
    public Flux<Store> findByIds(List<String> ids) {
        return firestoreStoreRepository.findAllById(ids)
                .map(StoreDocumentMapper::toDomain);
    }
}
