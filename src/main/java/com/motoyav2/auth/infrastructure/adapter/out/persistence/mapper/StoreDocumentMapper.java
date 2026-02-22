package com.motoyav2.auth.infrastructure.adapter.out.persistence.mapper;

import com.motoyav2.auth.domain.model.Store;
import com.motoyav2.auth.infrastructure.adapter.out.persistence.document.StoreDocument;

public final class StoreDocumentMapper {

    private StoreDocumentMapper() {
    }

    public static Store toDomain(StoreDocument doc) {
        return new Store(
                doc.getId(),
                doc.getBusinessName(),
                doc.getAddress(),
                doc.getCity()
        );
    }
}