package com.motoyav2.shared.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.motoyav2.auth.domain.port.out.UserRepository;
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
    private final UserRepository userRepository;

    public FirebaseAuthenticationFilter(
            @Autowired(required = false) FirebaseAuth firebaseAuth,
            @Autowired(required = false) UserRepository userRepository) {
        this.firebaseAuth   = firebaseAuth;
        this.userRepository = userRepository;
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
        return Mono.fromCallable(() -> firebaseAuth.verifyIdToken(token, false))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(firebaseToken -> {
                    Map<String, Object> claims = firebaseToken.getClaims();
                    String uid   = firebaseToken.getUid();
                    String email = firebaseToken.getEmail();
                    log.info("[FILTER] Token válido — uid={}, email={}", uid, email);

                    exchange.getAttributes().put("userId",    uid);
                    exchange.getAttributes().put("userEmail", email != null ? email : "");

                    Object nombreObj = claims.get("name");
                    String nombre = nombreObj instanceof String s ? s : email;
                    exchange.getAttributes().put("userNombre", nombre != null ? nombre : "");

                    String userType = (String) claims.get("userType");

                    FirebaseUserDetails userDetails = buildUserDetails(firebaseToken);
                    List<SimpleGrantedAuthority> authorities = extractAuthorities(firebaseToken);
                    FirebaseAuthenticationToken authentication =
                            new FirebaseAuthenticationToken(userDetails, authorities);

                    Mono<Void> continuar = chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

                    if (userType != null) {
                        exchange.getAttributes().put("userRol", userType);
                        populateStoreId(claims, exchange);
                        return continuar;
                    }

                    // Fallback: claims no tienen userType (Cloud Run sin permisos de custom claims)
                    if (userRepository == null) {
                        log.warn("[FILTER] userType ausente en claims y UserRepository no disponible — uid={}", uid);
                        return continuar;
                    }

                    return userRepository.findByUid(uid)
                            .doOnNext(user -> {
                                if (user.userType() != null) {
                                    exchange.getAttributes().put("userRol", user.userType());
                                    log.info("[FILTER] userRol cargado desde Firestore — uid={}, rol={}", uid, user.userType());
                                }
                                if (!"ADMIN".equals(user.userType())
                                        && user.storeIds() != null
                                        && !user.storeIds().isEmpty()) {
                                    exchange.getAttributes().put("storeId", user.storeIds().get(0));
                                }
                            })
                            .onErrorResume(e -> {
                                log.warn("[FILTER] Error al cargar usuario desde Firestore — uid={}: {}", uid, e.getMessage());
                                return Mono.empty();
                            })
                            .then(continuar);
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

    @SuppressWarnings("unchecked")
    private void populateStoreId(Map<String, Object> claims, ServerWebExchange exchange) {
        String userType = (String) claims.get("userType");
        if ("ADMIN".equals(userType)) return;
        Object storeIdsObj = claims.get("storeIds");
        if (storeIdsObj instanceof List<?> list && !list.isEmpty()) {
            exchange.getAttributes().put("storeId", list.get(0).toString());
        }
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
