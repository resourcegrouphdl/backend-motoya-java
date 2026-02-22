package com.motoyav2.auth.domain.port.in;

import com.motoyav2.auth.domain.model.LoginResult;
import com.motoyav2.auth.domain.model.TokenInfo;
import reactor.core.publisher.Mono;

public interface LoginUseCase {

    Mono<LoginResult> login(String uid, TokenInfo tokenInfo);
}
