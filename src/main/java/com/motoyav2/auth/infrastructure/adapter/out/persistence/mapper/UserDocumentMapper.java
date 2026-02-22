package com.motoyav2.auth.infrastructure.adapter.out.persistence.mapper;

import com.motoyav2.auth.domain.model.User;
import com.motoyav2.auth.infrastructure.adapter.out.persistence.document.UserDocument;

import java.util.List;

public final class UserDocumentMapper {

    private UserDocumentMapper() {
    }

    public static User toDomain(UserDocument doc) {
        return new User(
                doc.getUid(),
                doc.getFirstName(),
                doc.getLastName(),
                doc.getEmail(),
                doc.getUserType(),
                Boolean.TRUE.equals(doc.getIsActive()),
                Boolean.TRUE.equals(doc.getIsFirstLogin()),
                doc.getStoreIds() != null ? doc.getStoreIds() : List.of()
        );
    }
}
