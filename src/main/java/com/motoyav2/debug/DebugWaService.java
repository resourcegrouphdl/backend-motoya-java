package com.motoyav2.debug;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.motoyav2.notifications.infrastructure.channel.whatsapp.FactilizaWhatsAppNotificationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DebugWaService {

    private static final String COLLECTION = "debug_wa_mensajes";

    private final Firestore firestore;
    private final ObjectMapper objectMapper;
    private final FactilizaWhatsAppNotificationAdapter factilizaAdapter;

    /** Guarda el payload raw del webhook — llamado ANTES de cualquier otra lógica. */
    public Mono<Void> guardarPayload(Map<String, Object> payload) {
        return Mono.fromCallable(() -> {
            // Sanitizar base64 antes de serializar — evita límite de 1MB de Firestore
            Map<String, Object> sanitized = sanitizarBase64(payload);
            String json;
            try {
                json = objectMapper.writeValueAsString(sanitized);
            } catch (JsonProcessingException e) {
                json = sanitized.toString();
            }
            String evento       = String.valueOf(payload.getOrDefault("event",        "UNKNOWN"));
            String instanceName = String.valueOf(payload.getOrDefault("instanceName", ""));

            Map<String, Object> doc = new HashMap<>();
            doc.put("payloadJson",  json);
            doc.put("evento",       evento);
            doc.put("instanceName", instanceName);
            doc.put("direction",    "INBOUND");
            doc.put("receivedAt",   new Date());

            firestore.collection(COLLECTION).add(doc).get();
            log.debug("[DEBUG-WA] Payload guardado evento={}", evento);
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(e -> {
            log.warn("[DEBUG-WA] Error guardando payload: {}", e.getMessage());
            return Mono.empty();
        })
        .then();
    }

    /** Retorna los últimos 50 mensajes ordenados por fecha descendente. */
    public Flux<Map<String, Object>> listarRecientes() {
        return Mono.fromCallable(() -> {
            QuerySnapshot snap = firestore.collection(COLLECTION)
                    .orderBy("receivedAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get().get();
            return snap.getDocuments();
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable)
        .map(doc -> {
            Map<String, Object> data = new HashMap<>(doc.getData());
            data.put("id", doc.getId());
            Object ra = data.get("receivedAt");
            if (ra instanceof com.google.cloud.Timestamp ts) {
                data.put("receivedAt", ts.toDate().getTime());
            }
            return data;
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizarBase64(Map<String, Object> payload) {
        Object dataObj = payload.get("data");
        if (!(dataObj instanceof Map)) return payload;
        Object msgObj = ((Map<?, ?>) dataObj).get("Message");
        if (!(msgObj instanceof Map)) return payload;
        Object b64 = ((Map<?, ?>) msgObj).get("base64");
        if (!(b64 instanceof String b64Str) || b64Str.isBlank()) return payload;

        // Clonar sólo los niveles necesarios para no mutar el payload original
        Map<String, Object> msgCopy  = new HashMap<>((Map<String, Object>) msgObj);
        Map<String, Object> dataCopy = new HashMap<>((Map<String, Object>) dataObj);
        Map<String, Object> root     = new HashMap<>(payload);
        msgCopy.put("base64", "[BASE64_OMITIDO ~" + (b64Str.length() * 3 / 4 / 1024) + "kb]");
        dataCopy.put("Message", msgCopy);
        root.put("data", dataCopy);
        return root;
    }

    /** Envía un mensaje de texto via Factiliza y lo guarda en el log de debug. */
    public Mono<Map<String, Object>> enviarMensaje(String numero, String texto) {
        return factilizaAdapter.sendText(numero, texto)
                .flatMap(wamid -> Mono.fromCallable(() -> {
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("evento",       "SENT");
                    doc.put("instanceName", "ADMIN");
                    doc.put("direction",    "OUTBOUND");
                    doc.put("numero",       numero);
                    doc.put("texto",        texto);
                    doc.put("wamid",        wamid);
                    doc.put("receivedAt",   new Date());
                    doc.put("payloadJson",  objectMapper.writeValueAsString(doc));
                    firestore.collection(COLLECTION).add(doc).get();
                    return Map.<String, Object>of("status", "OK", "wamid", wamid, "numero", numero);
                }).subscribeOn(Schedulers.boundedElastic()));
    }
}
