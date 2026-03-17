package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.motoyav2.evaluacion.application.port.out.ReferenciaActualizacionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenciaActualizacionAdapter implements ReferenciaActualizacionPort {

    private final Firestore firestore;

    @Override
    public Mono<Void> actualizarVerificacion(String referenciaId, Map<String, Object> campos) {
        return Mono.fromCallable(() -> {
            Map<String, Object> updates = new HashMap<>(campos);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            updates.put("fechaContacto", FieldValue.serverTimestamp());

            firestore.collection("referencias")
                    .document(referenciaId)
                    .update(updates)
                    .get();
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then()
        .doOnSuccess(v -> log.info("referencias/{} verificación actualizada", referenciaId))
        .doOnError(e -> log.error("Error actualizando referencias/{}: {}", referenciaId, e.getMessage()));
    }
}
