package com.motoyav2.alertascenter.application.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.motoyav2.alertascenter.domain.model.*;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils;
import com.motoyav2.shared.exception.ConflictException;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertaCenterService {

    private final Firestore firestore;
    private final EnriquecedorAlertaService enriquecedor;
    private final FcmPushService fcmPushService;

    @Value("${alertas.collections.alertas:alertas_internas}")
    private String alertasCollection;

    // ─────────────────────────────────────────────────────────────────────────
    // PROCESAR EVENTO
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<AlertaInterna> procesarEvento(TipoAlerta tipo, SubTipoAlerta subTipo, String fuenteId) {
        return enriquecedor.enriquecer(tipo, subTipo, fuenteId)
                .flatMap(datos -> guardarAlerta(tipo, subTipo, fuenteId, datos))
                .flatMap(alerta -> {
                    Map<String, String> fcmData = Map.of(
                            "alertaId", alerta.id(),
                            "tipo", alerta.tipo().name()
                    );
                    return fcmPushService.enviarATodos(alerta.titulo(), alerta.mensaje(), fcmData)
                            .thenReturn(alerta);
                })
                .doOnSuccess(a -> log.info("Alerta creada: id={}, tipo={}", a.id(), a.tipo()))
                .doOnError(e -> log.error("Error procesando evento tipo={}, fuenteId={}: {}", tipo, fuenteId, e.getMessage()));
    }

    private Mono<AlertaInterna> guardarAlerta(TipoAlerta tipo, SubTipoAlerta subTipo,
                                               String fuenteId, AlertaDatosEnriquecidos datos) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("tipo", tipo.name());
        if (subTipo != null) doc.put("subTipo", subTipo.name());
        doc.put("titulo", datos.titulo());
        doc.put("mensaje", datos.mensaje());
        doc.put("payload", datos.payload());
        doc.put("estado", EstadoAlerta.PENDING.name());
        doc.put("asignadoA", null);
        doc.put("declines", new ArrayList<>());
        doc.put("fuenteId", fuenteId);
        doc.put("fuenteColeccion", datos.fuenteColeccion());
        doc.put("creadoEn", FieldValue.serverTimestamp());
        doc.put("actualizadoEn", FieldValue.serverTimestamp());

        DocumentReference ref = firestore.collection(alertasCollection).document();
        return FirestoreUtils.toMono(ref.set(doc))
                .map(writeResult -> {
                    Instant now = Instant.now();
                    return new AlertaInterna(ref.getId(), tipo, subTipo,
                            datos.titulo(), datos.mensaje(), datos.payload(),
                            EstadoAlerta.PENDING, null, new ArrayList<>(),
                            fuenteId, datos.fuenteColeccion(), now, now);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOMAR CASO — Transacción Firestore
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<AlertaInterna> tomarCaso(String alertaId, String userId, String email, String nombre) {
        DocumentReference ref = firestore.collection(alertasCollection).document(alertaId);

        return FirestoreUtils.toMono(firestore.runTransaction(transaction -> {
                    DocumentSnapshot snap;
                    try {
                        snap = transaction.get(ref).get();
                    } catch (Exception e) {
                        throw new RuntimeException("ERROR_LECTURA_FIRESTORE:" + e.getMessage(), e);
                    }

                    if (!snap.exists()) {
                        throw new RuntimeException("ALERTA_NO_ENCONTRADA");
                    }

                    String estadoActual = snap.getString("estado");
                    if (!EstadoAlerta.PENDING.name().equals(estadoActual)) {
                        throw new RuntimeException("ALERTA_NO_DISPONIBLE:estado=" + estadoActual);
                    }

                    Map<String, Object> asignadoA = new HashMap<>();
                    asignadoA.put("userId", userId);
                    asignadoA.put("email", email);
                    asignadoA.put("nombre", nombre);
                    asignadoA.put("fechaAsignacion", FieldValue.serverTimestamp());

                    transaction.update(ref, Map.of(
                            "estado", EstadoAlerta.TAKEN.name(),
                            "asignadoA", asignadoA,
                            "actualizadoEn", FieldValue.serverTimestamp()
                    ));
                    return null;
                }))
                .then(obtenerAlerta(alertaId))
                .onErrorMap(this::mapearErrorTransaccion)
                .doOnSuccess(a -> log.info("Alerta {} tomada por {}", alertaId, email));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DECLINAR CASO
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<AlertaInterna> declinarCaso(String alertaId, String userId, String email,
                                             String nombre, String motivo) {
        DocumentReference ref = firestore.collection(alertasCollection).document(alertaId);

        Map<String, Object> declineEntry = new HashMap<>();
        declineEntry.put("userId", userId);
        declineEntry.put("email", email);
        declineEntry.put("nombre", nombre);
        declineEntry.put("motivo", motivo != null ? motivo : "");
        declineEntry.put("timestamp", FieldValue.serverTimestamp());

        return FirestoreUtils.toMono(ref.update(
                        "declines", FieldValue.arrayUnion(declineEntry),
                        "actualizadoEn", FieldValue.serverTimestamp()
                ))
                .then(obtenerAlerta(alertaId))
                .doOnSuccess(a -> log.info("Alerta {} declinada por {}", alertaId, email));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<AlertaInterna> obtenerAlerta(String alertaId) {
        return FirestoreUtils.toMono(firestore.collection(alertasCollection).document(alertaId).get())
                .flatMap(snap -> {
                    if (!snap.exists()) {
                        return Mono.error(new NotFoundException("Alerta no encontrada: " + alertaId));
                    }
                    return Mono.just(mapearAlerta(snap));
                });
    }

    public Flux<AlertaInterna> listarAlertas(int limit, String estado) {
        CollectionReference col = firestore.collection(alertasCollection);
        Query query;

        if (estado != null && !estado.isBlank()) {
            query = col.whereEqualTo("estado", estado)
                    .orderBy("creadoEn", Query.Direction.DESCENDING)
                    .limit(limit);
        } else {
            query = col.orderBy("creadoEn", Query.Direction.DESCENDING)
                    .limit(limit);
        }

        return FirestoreUtils.toFlux(query.get())
                .map(this::mapearAlerta);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOKENS FCM
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<Void> registrarToken(String userId, String email, String token) {
        String docId = userId + "_" + Math.abs(token.hashCode());
        Map<String, Object> doc = new HashMap<>();
        doc.put("userId", userId);
        doc.put("email", email);
        doc.put("token", token);
        doc.put("plataforma", "web");
        doc.put("activo", true);
        doc.put("registradoEn", FieldValue.serverTimestamp());
        doc.put("ultimaActividad", FieldValue.serverTimestamp());

        return FirestoreUtils.toMono(firestore.collection("tokens_fcm").document(docId).set(doc)).then();
    }

    public Mono<Void> eliminarToken(String token) {
        return FirestoreUtils.toMono(
                        firestore.collection("tokens_fcm")
                                .whereEqualTo("token", token)
                                .get()
                )
                .flatMap(snapshot -> {
                    if (snapshot.isEmpty()) return Mono.empty();
                    DocumentReference ref = snapshot.getDocuments().get(0).getReference();
                    return FirestoreUtils.toMono(ref.update("activo", false));
                })
                .then();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPEO Firestore → Domain
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private AlertaInterna mapearAlerta(DocumentSnapshot snap) {
        Map<String, Object> data = snap.getData() != null ? snap.getData() : new HashMap<>();

        TipoAlerta tipo = parseEnum(TipoAlerta.class, (String) data.get("tipo"), TipoAlerta.NUEVA_SOLICITUD);
        EstadoAlerta estado = parseEnum(EstadoAlerta.class, (String) data.get("estado"), EstadoAlerta.PENDING);
        SubTipoAlerta subTipo = data.get("subTipo") != null
                ? parseEnum(SubTipoAlerta.class, (String) data.get("subTipo"), null) : null;

        AsignadoA asignadoA = null;
        if (data.get("asignadoA") instanceof Map<?, ?> m) {
            asignadoA = new AsignadoA(
                    (String) m.get("userId"),
                    (String) m.get("email"),
                    (String) m.get("nombre"),
                    toInstant(m.get("fechaAsignacion"))
            );
        }

        List<DeclineEntry> declines = new ArrayList<>();
        if (data.get("declines") instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> m) {
                    declines.add(new DeclineEntry(
                            (String) m.get("userId"),
                            (String) m.get("email"),
                            (String) m.get("nombre"),
                            (String) m.get("motivo"),
                            toInstant(m.get("timestamp"))
                    ));
                }
            }
        }

        Map<String, Object> payload = data.get("payload") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : new HashMap<>();

        return new AlertaInterna(
                snap.getId(), tipo, subTipo,
                (String) data.get("titulo"),
                (String) data.get("mensaje"),
                payload, estado, asignadoA, declines,
                (String) data.get("fuenteId"),
                (String) data.get("fuenteColeccion"),
                toInstant(data.get("creadoEn")),
                toInstant(data.get("actualizadoEn"))
        );
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) return ts.toDate().toInstant();
        if (value instanceof Date d) return d.toInstant();
        return Instant.now();
    }

    private <T extends Enum<T>> T parseEnum(Class<T> cls, String value, T fallback) {
        if (value == null) return fallback;
        try { return Enum.valueOf(cls, value); } catch (IllegalArgumentException e) { return fallback; }
    }

    private Throwable mapearErrorTransaccion(Throwable e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("ALERTA_NO_ENCONTRADA")) return new NotFoundException("Alerta no encontrada");
        if (msg.contains("ALERTA_NO_DISPONIBLE")) return new ConflictException("Alerta ya fue tomada por otro usuario");
        return e;
    }
}
