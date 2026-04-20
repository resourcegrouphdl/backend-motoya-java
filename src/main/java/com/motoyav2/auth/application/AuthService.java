package com.motoyav2.auth.application;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.motoyav2.auth.domain.model.LoginResult;
import com.motoyav2.auth.domain.model.TokenInfo;
import com.motoyav2.auth.domain.port.in.LoginUseCase;
import com.motoyav2.auth.domain.port.out.StoreRepository;
import com.motoyav2.auth.domain.port.out.UserRepository;
import com.motoyav2.gestion.domain.model.ModuloPermiso;
import com.motoyav2.shared.exception.ForbiddenException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AuthService implements LoginUseCase {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final FirebaseAuth firebaseAuth;

    public AuthService(
            UserRepository userRepository,
            StoreRepository storeRepository,
            @Autowired(required = false) FirebaseAuth firebaseAuth) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public Mono<LoginResult> login(String uid, TokenInfo tokenInfo) {
        log.info("[AUTH] Login iniciado para uid={}", uid);
        return userRepository.findByUid(uid)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[AUTH] Usuario no encontrado en Firestore para uid={}", uid);
                    return Mono.error(new NotFoundException("User not found: " + uid));
                }))
                .flatMap(user -> {
                    log.info("[AUTH] Usuario encontrado: uid={}, email={}, userType={}, active={}, modulos={}",
                            uid, user.email(), user.userType(), user.active(),
                            user.modulos() != null ? user.modulos().size() : 0);

                    if (!user.active()) {
                        log.warn("[AUTH] Cuenta inactiva para uid={}", uid);
                        return Mono.error(new ForbiddenException("User account is inactive"));
                    }

                    // Calcular módulos efectivos (Firestore → fallback a defaults por rol)
                    List<String> modulos = (user.modulos() != null && !user.modulos().isEmpty())
                            ? user.modulos()
                            : ModuloPermiso.defaultForRole(user.userType());

                    log.info("[AUTH] Módulos efectivos para uid={}: {}", uid, modulos);

                    // Setear custom claims en Firebase (no bloquea el hilo principal)
                    Mono<Void> setClaimsMono = setCustomClaims(uid, user.userType(), modulos);

                    List<String> storeIds = user.storeIds();
                    Mono<LoginResult> buildResult = (storeIds == null || storeIds.isEmpty())
                            ? Mono.just(new LoginResult(tokenInfo, user, List.of()))
                            : storeRepository.findByIds(storeIds)
                                    .collectList()
                                    .map(stores -> {
                                        log.info("[AUTH] Tiendas cargadas para uid={}: {}", uid, stores.size());
                                        return new LoginResult(tokenInfo, user, stores);
                                    });

                    return setClaimsMono.then(buildResult)
                            .doOnSuccess(r -> log.info("[AUTH] Login completado exitosamente para uid={}", uid));
                })
                .doOnError(e -> log.error("[AUTH] Error en login para uid={}: {}", uid, e.getMessage()));
    }

    private Mono<Void> setCustomClaims(String uid, String userType, List<String> modulos) {
        if (firebaseAuth == null) {
            log.warn("[AUTH] FirebaseAuth no disponible, custom claims NO se setean");
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
            try {
                log.info("[AUTH] Seteando custom claims para uid={}, userType={}, modulos={}", uid, userType, modulos);
                Map<String, Object> claims = new HashMap<>();
                claims.put("userType", userType);
                claims.put("modulos", modulos);
                claims.put("roles", List.of(userType.toUpperCase()));
                firebaseAuth.setCustomUserClaims(uid, claims);
                log.info("[AUTH] Custom claims seteados correctamente para uid={}", uid);
            } catch (FirebaseAuthException e) {
                log.warn("[AUTH] Failed to set custom claims for uid={}: {}", uid, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
