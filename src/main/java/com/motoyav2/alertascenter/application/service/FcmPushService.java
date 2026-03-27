package com.motoyav2.alertascenter.application.service;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.messaging.*;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmPushService {

    private final FirebaseMessaging firebaseMessaging;
    private final Firestore firestore;

    @Value("${alertas.collections.tokens-fcm:tokens_fcm}")
    private String tokensFcmCollection;

    public Mono<Void> enviarATodos(String titulo, String mensaje, Map<String, String> data) {
        return fetchTokensActivos()
                .flatMap(tokens -> {
                    if (tokens.isEmpty()) {
                        log.info("No hay tokens FCM activos — omitiendo push");
                        return Mono.empty();
                    }
                    log.info("Enviando push a {} dispositivos activos", tokens.size());
                    return enviarMulticast(tokens, titulo, mensaje, data);
                })
                .onErrorResume(e -> {
                    log.error("Error en FCM push (no crítico): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<List<String>> fetchTokensActivos() {
        return FirestoreUtils.toMono(
                firestore.collection(tokensFcmCollection)
                        .whereEqualTo("activo", true)
                        .get()
        ).map(snapshot -> snapshot.getDocuments().stream()
                .map(doc -> doc.getString("token"))
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.toList())
        ).onErrorReturn(Collections.emptyList());
    }

    private Mono<Void> enviarMulticast(List<String> tokens, String titulo, String mensaje, Map<String, String> data) {
        return Flux.fromIterable(particionarEn(tokens, 500))
                .flatMap(lote -> enviarLote(lote, titulo, mensaje, data))
                .then();
    }

    private Mono<Void> enviarLote(List<String> tokens, String titulo, String mensaje, Map<String, String> data) {
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(titulo)
                        .setBody(mensaje)
                        .build())
                .putAllData(data != null ? data : new HashMap<>())
                .setWebpushConfig(WebpushConfig.builder()
                        .setNotification(WebpushNotification.builder()
                                .setTitle(titulo)
                                .setBody(mensaje)
                                .setIcon("/assets/icons/icon-192x192.png")
                                .setBadge("/assets/icons/badge-72x72.png")
                                .setRequireInteraction(true)
                                .build())
                        .putHeader("Urgency", "high")
                        .build())
                .build();

        return FirestoreUtils.toMono(firebaseMessaging.sendEachForMulticastAsync(message))
                .doOnNext(response -> {
                    log.info("FCM lote: {} exitosos / {} fallidos de {} tokens",
                            response.getSuccessCount(), response.getFailureCount(), tokens.size());
                    response.getResponses().stream()
                            .filter(r -> !r.isSuccessful())
                            .forEach(r -> log.warn("Token FCM inválido — error: {}",
                                    r.getException() != null ? r.getException().getMessage() : "desconocido"));
                })
                .onErrorResume(e -> {
                    log.error("Error enviando lote FCM: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private <T> List<List<T>> particionarEn(List<T> lista, int tamaño) {
        List<List<T>> particiones = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += tamaño) {
            particiones.add(lista.subList(i, Math.min(i + tamaño, lista.size())));
        }
        return particiones;
    }
}
