package com.motoyav2.auth.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.auth.domain.model.User;
import com.motoyav2.auth.domain.port.out.UserRepository;
import com.motoyav2.auth.infrastructure.adapter.out.persistence.mapper.UserDocumentMapper;
import com.motoyav2.auth.infrastructure.adapter.out.persistence.repository.FirestoreUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final FirestoreUserRepository firestoreUserRepository;

    @Override
    public Mono<User> findByUid(String uid) {
        return firestoreUserRepository.findById(uid)
                .map(UserDocumentMapper::toDomain);
    }
}
