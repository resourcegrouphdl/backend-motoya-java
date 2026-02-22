package com.motoyav2.auth.infrastructure.adapter.in.web;

import com.motoyav2.auth.domain.model.TokenInfo;
import com.motoyav2.auth.domain.port.in.LoginUseCase;
import com.motoyav2.auth.infrastructure.adapter.in.web.dto.LoginResponse;
import com.motoyav2.auth.infrastructure.adapter.in.web.mapper.LoginResponseMapper;
import com.motoyav2.shared.security.FirebaseUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final LoginUseCase loginUseCase;

    @PostMapping("/login")
    public Mono<LoginResponse> login(
            @AuthenticationPrincipal FirebaseUserDetails principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        String token = authHeader.substring(BEARER_PREFIX.length());
        long expiresIn = calculateExpiresIn(principal.claims());
        TokenInfo tokenInfo = new TokenInfo(token, expiresIn);

        return loginUseCase.login(principal.uid(), tokenInfo)
                .map(LoginResponseMapper::toResponse);
    }

    private long calculateExpiresIn(Map<String, Object> claims) {
        Object exp = claims.get("exp");
        if (exp instanceof Number expNumber) {
            long expirationEpochSeconds = expNumber.longValue();
            long nowEpochSeconds = Instant.now().getEpochSecond();
            return Math.max(0, expirationEpochSeconds - nowEpochSeconds);
        }
        return 0;
    }
}
