package com.motoyav2.notifications.infrastructure.persistence.adapter;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.motoyav2.notifications.domain.model.conversacion.*;
import com.motoyav2.notifications.domain.port.out.ConversacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversacionRepositoryAdapter implements ConversacionRepository {

    private static final String COL = "conversaciones_whatsapp";
    private final Firestore db;

    @Override
    public Mono<Void> upsertConversacion(ConversacionWa conv) {
        Map<String, Object> mutableFields = new HashMap<>();
        mutableFields.put("solicitudId",        conv.solicitudId());
        mutableFields.put("telefono",           conv.telefono());
        mutableFields.put("nombreParticipante", conv.nombreParticipante());
        mutableFields.put("rol",                conv.rol().name());
        mutableFields.put("estadoConversacion", conv.estadoConversacion().name());
        mutableFields.put("ultimaActividad",    toTs(conv.ultimaActividad()));

        Map<String, Object> createData = new HashMap<>(mutableFields);
        createData.put("createdAt", FieldValue.serverTimestamp());

        DocumentReference ref = db.collection(COL).document(conv.id());
        // Try to create (sets createdAt only on first write); if doc already exists, just update mutable fields
        return toMono(ref.create(createData))
                .onErrorResume(e -> toMono(ref.set(mutableFields,
                        SetOptions.mergeFields("solicitudId", "telefono", "nombreParticipante",
                                "rol", "estadoConversacion", "ultimaActividad"))))
                .then()
                .onErrorResume(e -> {
                    log.error("[CONV-REPO] Error upsert conversacion={}: {}", conv.id(), e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> agregarMensaje(String conversacionId, MensajeWa msg) {
        Map<String, Object> msgMap = buildMensajeMap(msg);

        Map<String, Object> updates = new HashMap<>();
        updates.put("mensajes",        FieldValue.arrayUnion(msgMap));
        updates.put("ultimaActividad", FieldValue.serverTimestamp());

        return toMono(db.collection(COL).document(conversacionId).set(updates, SetOptions.merge()))
                .then()
                .onErrorResume(e -> {
                    log.error("[CONV-REPO] Error agregando mensaje a conv={}: {}", conversacionId, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<ConversacionWa> findById(String id) {
        return toMono(db.collection(COL).document(id).get())
                .mapNotNull(doc -> doc.exists() ? toDomain(id, doc.getData()) : null);
    }

    @Override
    public Flux<ConversacionWa> findBySolicitudId(String solicitudId) {
        return Mono.fromCallable(() ->
                        db.collection(COL)
                                .whereEqualTo("solicitudId", solicitudId)
                                .orderBy("ultimaActividad", com.google.cloud.firestore.Query.Direction.DESCENDING)
                                .get().get())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .mapNotNull(doc -> toDomain(doc.getId(), doc.getData()))
                .onErrorResume(e -> {
                    log.error("[CONV-REPO] Error findBySolicitudId={}: {}", solicitudId, e.getMessage());
                    return Flux.empty();
                });
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ConversacionWa toDomain(String id, Map<String, Object> data) {
        if (data == null) return null;
        List<MensajeWa> mensajes = new ArrayList<>();
        Object mensajesRaw = data.get("mensajes");
        if (mensajesRaw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map) {
                    mensajes.add(toMensajeDomain((Map<String, Object>) item));
                }
            }
        }
        return new ConversacionWa(
                id,
                str(data, "solicitudId"),
                str(data, "telefono"),
                str(data, "nombreParticipante"),
                parseEnum(RolParticipante.class, str(data, "rol"), RolParticipante.TITULAR),
                parseEnum(EstadoConversacion.class, str(data, "estadoConversacion"), EstadoConversacion.INICIADA),
                mensajes,
                toInstant(data.get("ultimaActividad")),
                toInstant(data.get("createdAt"))
        );
    }

    private MensajeWa toMensajeDomain(Map<String, Object> m) {
        return new MensajeWa(
                str(m, "id"),
                parseEnum(DireccionMensaje.class, str(m, "direccion"), DireccionMensaje.INBOUND),
                parseEnum(TipoMensajeWa.class, str(m, "tipo"), TipoMensajeWa.TEXTO),
                str(m, "contenido"),
                str(m, "mediaUrl"),
                str(m, "storageRef"),
                str(m, "enviadorNombre"),
                str(m, "claudeClasificacion"),
                m.get("claudeConfianza") instanceof Number n ? n.doubleValue() : null,
                toInstant(m.get("timestamp"))
        );
    }

    private Map<String, Object> buildMensajeMap(MensajeWa msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",        msg.id() != null ? msg.id() : UUID.randomUUID().toString());
        m.put("direccion", msg.direccion().name());
        m.put("tipo",      msg.tipo().name());
        m.put("contenido", msg.contenido() != null ? msg.contenido() : "");
        m.put("timestamp", toTs(msg.timestamp()));
        if (msg.mediaUrl()          != null) m.put("mediaUrl",          msg.mediaUrl());
        if (msg.storageRef()        != null) m.put("storageRef",        msg.storageRef());
        if (msg.enviadorNombre()    != null) m.put("enviadorNombre",    msg.enviadorNombre());
        if (msg.claudeClasificacion() != null) m.put("claudeClasificacion", msg.claudeClasificacion());
        if (msg.claudeConfianza()   != null) m.put("claudeConfianza",   msg.claudeConfianza());
        return m;
    }

    private Timestamp toTs(Instant instant) {
        if (instant == null) return Timestamp.now();
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) return ts.toDate().toInstant();
        return null;
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof String s ? s : null;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> cls, String value, E fallback) {
        if (value == null) return fallback;
        try { return Enum.valueOf(cls, value); }
        catch (IllegalArgumentException e) { return fallback; }
    }
}
