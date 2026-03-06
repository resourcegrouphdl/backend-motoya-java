package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.common.util.concurrent.MoreExecutors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

public final class FirestoreReactiveUtils {

    private FirestoreReactiveUtils() {}

    public static <T> Mono<T> toMono(ApiFuture<T> future) {
        return Mono.fromFuture(() -> {
            CompletableFuture<T> cf = new CompletableFuture<>();
            ApiFutures.addCallback(future, new ApiFutureCallback<>() {
                @Override public void onSuccess(T result) { cf.complete(result); }
                @Override public void onFailure(Throwable t) { cf.completeExceptionally(t); }
            }, MoreExecutors.directExecutor());
            return cf;
        });
    }

    public static Flux<DocumentSnapshot> toFlux(ApiFuture<QuerySnapshot> future) {
        return toMono(future)
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()));
    }
}
