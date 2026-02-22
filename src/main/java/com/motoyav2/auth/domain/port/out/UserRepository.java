package com.motoyav2.auth.domain.port.out;

import com.motoyav2.auth.domain.model.User;
import reactor.core.publisher.Mono;

public interface UserRepository {

    Mono<User> findByUid(String uid);
}
