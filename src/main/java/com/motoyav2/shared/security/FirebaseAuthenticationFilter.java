package com.motoyav2.shared.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class FirebaseAuthenticationFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthenticationFilter(@Autowired(required = false) FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
        if (firebaseAuth == null) {
            log.warn("FirebaseAuth not available. Token verification is DISABLED.");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (firebaseAuth == null) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        String path  = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        log.info("[FILTER] {} {} — verificando token (primeros 20 chars: {}...)", method, path, token.substring(0, Math.min(20, token.length())));

        // checkRevoked=true: detecta tokens revocados inmediatamente (cuando admin cambia permisos)
        return Mono.fromCallable(() -> firebaseAuth.verifyIdToken(token, true))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(firebaseToken -> {
                    log.info("[FILTER] Token válido — uid={}, email={}", firebaseToken.getUid(), firebaseToken.getEmail());
                    FirebaseUserDetails userDetails = buildUserDetails(firebaseToken);
                    List<SimpleGrantedAuthority> authorities = extractAuthorities(firebaseToken);
                    FirebaseAuthenticationToken authentication =
                            new FirebaseAuthenticationToken(userDetails, authorities);

                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                })
                .onErrorResume(e -> {
                    log.warn("[FILTER] Token inválido para {} {}: {}", method, path, e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    String body = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Token inválido o revocado\"}";
                    return exchange.getResponse().writeWith(
                            Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes()))
                    );
                });
    }

    private FirebaseUserDetails buildUserDetails(FirebaseToken token) {
        return new FirebaseUserDetails(
                token.getUid(),
                token.getEmail(),
                token.isEmailVerified(),
                token.getClaims()
        );
    }

    @SuppressWarnings("unchecked")
    private List<SimpleGrantedAuthority> extractAuthorities(FirebaseToken token) {
        Map<String, Object> claims = token.getClaims();
        Object rolesObj = claims.get("roles");

        if (rolesObj instanceof List<?> roles) {
            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + ((String) role).toUpperCase()))
                    .toList();
        }

        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
