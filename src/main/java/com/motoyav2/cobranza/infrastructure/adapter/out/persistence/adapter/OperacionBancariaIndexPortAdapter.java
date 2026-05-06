package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.cobranza.application.port.out.OperacionBancariaIndexPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.OperacionBancariaIndexDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OperacionBancariaIndexPortAdapter implements OperacionBancariaIndexPort {

    private static final String COLLECTION = "cobranzas-operaciones-index";

    private final Firestore firestore;

    /**
     * Usa DocumentReference.create() — falla atómicamente con ALREADY_EXISTS si el documento
     * ya existe, eliminando cualquier condición de carrera sin necesidad de transacción explícita.
     */
    @Override
    public Mono<Boolean> registrarSiNueva(String banco, String numeroOperacion,
                                           OperacionBancariaIndexDocument datos) {
        String key = buildKey(banco, numeroOperacion);
        return Mono.fromCallable(() -> {
            Map<String, Object> data = new HashMap<>();
            data.put("bancoRaw",           datos.getBancoRaw());
            data.put("numeroOperacionRaw", datos.getNumeroOperacionRaw());
            data.put("voucherId",          datos.getVoucherId()      != null ? datos.getVoucherId()      : "");
            data.put("contratoId",         datos.getContratoId()     != null ? datos.getContratoId()     : "");
            data.put("monto",              datos.getMonto()          != null ? datos.getMonto()          : 0.0);
            data.put("fechaOperacion",     datos.getFechaOperacion() != null ? datos.getFechaOperacion() : "");
            data.put("creadoEn",           new Date());

            try {
                firestore.collection(COLLECTION).document(key).create(data).get();
                log.info("[OperacionIndex] Registrada nueva operacion: {}", key);
                return true;
            } catch (ExecutionException e) {
                if (isAlreadyExists(e)) {
                    log.warn("[OperacionIndex] Operacion ya existia (duplicado detectado): {}", key);
                    return false;
                }
                throw new RuntimeException("Error al registrar en indice de operaciones: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<OperacionBancariaIndexDocument> buscarDuplicado(String banco, String numeroOperacion) {
        String key = buildKey(banco, numeroOperacion);
        return Mono.fromCallable(() ->
                firestore.collection(COLLECTION).document(key).get().get()
        ).flatMap(snapshot -> {
            if (!snapshot.exists()) return Mono.empty();
            OperacionBancariaIndexDocument doc = OperacionBancariaIndexDocument.builder()
                    .id(snapshot.getId())
                    .bancoRaw(snapshot.getString("bancoRaw"))
                    .numeroOperacionRaw(snapshot.getString("numeroOperacionRaw"))
                    .voucherId(snapshot.getString("voucherId"))
                    .contratoId(snapshot.getString("contratoId"))
                    .monto(snapshot.getDouble("monto"))
                    .fechaOperacion(snapshot.getString("fechaOperacion"))
                    .build();
            return Mono.just(doc);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> eliminar(String banco, String numeroOperacion) {
        String key = buildKey(banco, numeroOperacion);
        return Mono.fromCallable(() -> {
            firestore.collection(COLLECTION).document(key).delete().get();
            log.info("[OperacionIndex] Entrada eliminada (rollback): {}", key);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildKey(String banco, String numeroOperacion) {
        String b = banco.toUpperCase().replaceAll("[^A-Z0-9]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        String n = numeroOperacion.toUpperCase().replaceAll("[^A-Z0-9]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        return b + "_" + n;
    }

    private boolean isAlreadyExists(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause == null) return false;
        String className = cause.getClass().getName();
        String message   = cause.getMessage() != null ? cause.getMessage() : "";
        return className.contains("AlreadyExists") || message.contains("ALREADY_EXISTS");
    }
}