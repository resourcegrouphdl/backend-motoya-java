package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.motoyav2.evaluacion.application.port.out.ClienteActualizacionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter que realiza actualizaciones parciales en clientes_v1
 * usando el Firestore SDK directamente (no Spring Data, para evitar reemplazar el doc completo).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClienteActualizacionAdapter implements ClienteActualizacionPort {

    private final Firestore firestore;

    @Override
    public Mono<Void> actualizarEvaluacionDocumentos(String clienteId,
                                                      Map<String, Object> evaluacionDocumentos,
                                                      String estadoValidacion,
                                                      List<String> documentosObservados) {
        return Mono.fromCallable(() -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put("evaluacionDocumentos", evaluacionDocumentos);
            updates.put("estadoValidacionDocumentos", estadoValidacion);
            updates.put("documentosObservados", documentosObservados != null ? documentosObservados : List.of());
            updates.put("updatedAt", FieldValue.serverTimestamp());
            updates.put("fechaValidacionDocumentos", FieldValue.serverTimestamp());

            firestore.collection("clientes_v1")
                    .document(clienteId)
                    .update(updates)
                    .get(); // bloquear para obtener el ApiFuture — se ejecuta en boundedElastic
            return null;
        })
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .then()
        .doOnSuccess(v -> log.info("evaluacionDocumentos actualizado en clientes_v1/{}", clienteId))
        .doOnError(e -> log.error("Error actualizando evaluacionDocumentos en {}: {}", clienteId, e.getMessage()));
    }
}
