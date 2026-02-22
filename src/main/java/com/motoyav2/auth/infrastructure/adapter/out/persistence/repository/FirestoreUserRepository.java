package com.motoyav2.auth.infrastructure.adapter.out.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.auth.infrastructure.adapter.out.persistence.document.UserDocument;

public interface FirestoreUserRepository extends FirestoreReactiveRepository<UserDocument> {
}
