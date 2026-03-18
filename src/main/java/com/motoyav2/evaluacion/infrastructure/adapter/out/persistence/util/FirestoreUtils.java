package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.common.util.concurrent.MoreExecutors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

public final class FirestoreUtils {

    private FirestoreUtils() {}

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
        return toMono(future).flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()));
    }

    /** Convierte un valor de Firestore (que puede ser String o Number) a int. */
    public static int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public static double toDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
