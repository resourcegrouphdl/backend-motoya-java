package com.motoyav2.auth.application;

import com.motoyav2.auth.domain.model.LoginResult;
import com.motoyav2.auth.domain.model.TokenInfo;
import com.motoyav2.auth.domain.port.in.LoginUseCase;
import com.motoyav2.auth.domain.port.out.StoreRepository;
import com.motoyav2.auth.domain.port.out.UserRepository;
import com.motoyav2.shared.exception.ForbiddenException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService implements LoginUseCase {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;

    @Override
    public Mono<LoginResult> login(String uid, TokenInfo tokenInfo) {
        return userRepository.findByUid(uid)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found: " + uid)))
                .flatMap(user -> {
                    if (!user.active()) {
                        return Mono.error(new ForbiddenException("User account is inactive"));
                    }

                    List<String> storeIds = user.storeIds();
                    if (storeIds == null || storeIds.isEmpty()) {
                        return Mono.just(new LoginResult(tokenInfo, user, List.of()));
                    }

                    return storeRepository.findByIds(storeIds)
                            .collectList()
                            .map(stores -> new LoginResult(tokenInfo, user, stores));
                });
    }
}
