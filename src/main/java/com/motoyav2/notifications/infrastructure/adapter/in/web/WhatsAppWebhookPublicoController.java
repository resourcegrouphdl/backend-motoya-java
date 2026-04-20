package com.motoyav2.notifications.infrastructure.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motoyav2.notifications.domain.port.in.WhatsAppMessageDispatcher;
import com.motoyav2.notifications.infrastructure.adapter.out.storage.WhatsAppMediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Webhook público de Factiliza/json.pe — configurado en el panel de Factiliza.
 *   POST https://backend-motoya-java-26647667439.europe-west1.run.app/webhook/whatsapp
 *
 * Tipos de eventos manejados:
 *   - Mensaje de texto entrante  → dispatcher → Claude
 *   - Mensaje de media (base64)  → subir GCS → dispatcher
 *   - ACK / sent / delivered / read → solo log, sin procesamiento
 *
 * Siempre responde 200 para que Factiliza no reintente.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookPublicoController {

    private static final Set<String> MEDIA_TYPES = Set.of("image", "document", "audio", "video", "sticker", "ptt");

    private final WhatsAppMessageDispatcher      dispatcher;
    private final WhatsAppMediaStorageService    mediaStorage;
    private final ObjectMapper                   objectMapper;

    @Value("${notifications.webhook.debug-payload:true}")
    private boolean debugPayload;

    @Value("${notifications.webhook.token:}")
    private String webhookToken;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, String>> receive(
            @RequestParam(name = "token", required = false) String tokenParam,
            @RequestBody Map<String, Object> payload) {

        if (webhookToken != null && !webhookToken.isBlank() && !webhookToken.equals(tokenParam)) {
            log.warn("[WEBHOOK] Token inválido — ignorado");
            return Mono.just(Map.of("status", "received"));
        }

        if (debugPayload) {
            logRawPayload(payload);
        }

        // ── Determinar tipo de evento ─────────────────────────────────────────
        String eventType = extractEventType(payload);
        log.info("[WEBHOOK] Evento recibido | type={} keys={}", eventType, payload.keySet());

        if (isStatusEvent(eventType)) {
            log.info("[WEBHOOK] Evento de estado (ignorado): type={}", eventType);
            return Mono.just(Map.of("status", "received"));
        }

        // ── Mensaje entrante (texto o media) ──────────────────────────────────
        String from = extractFrom(payload);
        log.info("[WEBHOOK] from extraído={}", from);
        if (from == null || from.isBlank()) {
            log.warn("[WEBHOOK] Sin 'from' reconocible — ignorado. Keys disponibles: {}", payload.keySet());
            return Mono.just(Map.of("status", "received"));
        }

        String   text      = extractText(payload);
        String   mediaType = extractMediaType(payload);
        String   base64    = extractBase64(payload);
        String   filename  = extractFilename(payload);

        log.info("[WEBHOOK] Parseado | from={} text={} mediaType={} tieneBase64={}", from, text, mediaType, base64 != null);

        if (text != null) {
            // ── Mensaje de texto ─────────────────────────────────────────────
            log.info("[WEBHOOK] Despachando texto | from={} text={}", from,
                    text.length() > 60 ? text.substring(0, 60) + "…" : text);
            dispatcher.dispatch(from, text, null, null)
                    .subscribe(null, ex -> log.warn("[WEBHOOK] Error dispatch texto phone={}: {}", from, ex.getMessage()));

        } else if (base64 != null && mediaType != null) {
            // ── Mensaje de media en base64 → subir GCS → dispatcher ──────────
            log.info("[WEBHOOK] Media base64 | from={} type={} file={}", from, mediaType, filename);
            mediaStorage.subirBase64(base64, mediaType, filename, null)
                    .flatMap(url -> dispatcher.dispatch(from, null, mediaType, url))
                    .subscribe(null, ex -> log.warn("[WEBHOOK] Error procesando media phone={}: {}", from, ex.getMessage()));

        } else {
            log.warn("[WEBHOOK] Sin contenido reconocible | from={} eventType={} payloadKeys={}", from, eventType, payload.keySet());
        }

        return Mono.just(Map.of("status", "received"));
    }

    // ── Helpers de parsing ────────────────────────────────────────────────────

    /** Extrae el tipo de evento: "message", "ack", "sent", "read", "delivered", etc. */
    private String extractEventType(Map<String, Object> p) {
        Object t = p.get("type");
        if (t instanceof String s) return s.toLowerCase();
        Object ev = p.get("event");
        if (ev instanceof String s) return s.toLowerCase();
        // json.pe puede usar "event" anidado
        Object data = p.get("data");
        if (data instanceof Map<?, ?> d) {
            Object dt = d.get("type");
            if (dt instanceof String s) return s.toLowerCase();
        }
        return "message";
    }

    private boolean isStatusEvent(String eventType) {
        if (eventType == null) return false;
        return eventType.contains("ack") || eventType.contains("sent")
                || eventType.contains("delivered") || eventType.contains("read")
                || eventType.contains("status");
    }

    @SuppressWarnings("unchecked")
    private String extractFrom(Map<String, Object> p) {
        try {
            // Directo: "from"
            if (p.get("from") instanceof String s && !s.isBlank()) return cleanPhone(s);
            // Directo: "number", "phone"
            if (p.get("number") instanceof String s && !s.isBlank()) return cleanPhone(s);
            if (p.get("phone")  instanceof String s && !s.isBlank()) return cleanPhone(s);
            // Anidado: data.from / data.key.remoteJid
            if (p.get("data") instanceof Map<?, ?> d) {
                if (d.get("from") instanceof String s && !s.isBlank()) return cleanPhone(s);
                if (d.get("key") instanceof Map<?, ?> k && k.get("remoteJid") instanceof String s) return cleanPhone(s);
            }
            return null;
        } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> p) {
        try {
            // Comprueba primero que NO sea media
            if (Boolean.TRUE.equals(p.get("isMedia"))) return null;
            String type = p.get("type") instanceof String t ? t.toLowerCase() : "";
            if (MEDIA_TYPES.contains(type)) return null;

            // Formato json.pe más común: "body" directo (solo si type = chat/text/conversation)
            if (p.get("body") instanceof String b && !b.isBlank()
                    && !looksLikeBase64(b)) return b;
            if (p.get("text") instanceof String t && !t.isBlank()) return t;

            // Anidado message.*
            if (p.get("message") instanceof Map<?, ?> msg) {
                if (msg.get("body")         instanceof String s && !s.isBlank()) return s;
                if (msg.get("text")         instanceof String s && !s.isBlank()) return s;
                if (msg.get("conversation") instanceof String s && !s.isBlank()) return s;
                if (msg.get("text") instanceof Map<?, ?> tm && tm.get("body") instanceof String s) return s;
                if (msg.get("extendedTextMessage") instanceof Map<?, ?> em
                        && em.get("text") instanceof String s) return s;
            }

            // Baileys: data.message.conversation
            if (p.get("data") instanceof Map<?, ?> d) {
                if (d.get("body") instanceof String s && !s.isBlank() && !looksLikeBase64(s)) return s;
                if (d.get("message") instanceof Map<?, ?> dm) {
                    if (dm.get("conversation") instanceof String s) return s;
                    if (dm.get("extendedTextMessage") instanceof Map<?, ?> em
                            && em.get("text") instanceof String s) return s;
                }
            }
            return null;
        } catch (Exception e) { return null; }
    }

    /** Retorna el tipo de media si el mensaje es un archivo, null si es texto. */
    @SuppressWarnings("unchecked")
    private String extractMediaType(Map<String, Object> p) {
        try {
            // Formato json.pe: isMedia=true, type=image/document
            if (Boolean.TRUE.equals(p.get("isMedia"))) {
                return p.get("type") instanceof String t ? t.toLowerCase() : "document";
            }
            // type directamente es un tipo de media
            String type = p.get("type") instanceof String t ? t.toLowerCase() : null;
            if (type != null && MEDIA_TYPES.contains(type)) return type;

            // body parece base64 → asumir document
            if (p.get("body") instanceof String b && looksLikeBase64(b)) return "document";

            // data.type
            if (p.get("data") instanceof Map<?, ?> d && d.get("type") instanceof String dt) {
                String lower = dt.toLowerCase();
                if (MEDIA_TYPES.contains(lower)) return lower;
            }
            return null;
        } catch (Exception e) { return null; }
    }

    /** Extrae el contenido base64 del media. */
    @SuppressWarnings("unchecked")
    private String extractBase64(Map<String, Object> p) {
        try {
            // json.pe: "body" contiene el base64 cuando isMedia=true
            if (p.get("body") instanceof String b && looksLikeBase64(b)) return b;
            if (p.get("data64") instanceof String d) return d;
            if (p.get("base64") instanceof String b) return b;

            // Anidado: data.body
            if (p.get("data") instanceof Map<?, ?> d) {
                if (d.get("body")   instanceof String s && looksLikeBase64(s)) return s;
                if (d.get("base64") instanceof String s) return s;
            }
            // message.{type}.data
            if (p.get("message") instanceof Map<?, ?> msg) {
                String type = extractMediaType(p);
                if (type != null && msg.get(type) instanceof Map<?, ?> media) {
                    if (media.get("data")   instanceof String s) return s;
                    if (media.get("base64") instanceof String s) return s;
                }
            }
            return null;
        } catch (Exception e) { return null; }
    }

    /** Extrae el nombre de archivo si lo trae el payload. */
    @SuppressWarnings("unchecked")
    private String extractFilename(Map<String, Object> p) {
        try {
            if (p.get("filename") instanceof String s) return s;
            if (p.get("fileName") instanceof String s) return s;
            if (p.get("caption")  instanceof String s && s.contains(".")) return s;
            if (p.get("message")  instanceof Map<?, ?> msg) {
                String type = extractMediaType(p);
                if (type != null && msg.get(type) instanceof Map<?, ?> media) {
                    if (media.get("fileName") instanceof String s) return s;
                    if (media.get("filename") instanceof String s) return s;
                    if (media.get("title")    instanceof String s) return s;
                }
            }
            return null;
        } catch (Exception e) { return null; }
    }

    /** Heurística rápida: ¿parece base64? Largo, sin espacios, caracteres válidos. */
    private boolean looksLikeBase64(String s) {
        if (s == null || s.length() < 100) return false;
        if (s.startsWith("data:"))         return true;
        // Al menos 90% de los caracteres son base64 válidos
        long valid = s.chars().filter(c -> Character.isLetterOrDigit(c) || c == '+' || c == '/' || c == '=').count();
        return (double) valid / s.length() > 0.9;
    }

    /** Elimina @s.whatsapp.net y normaliza a 9 dígitos peruanos. */
    private String cleanPhone(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("@.*$", "").replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) return digits.substring(2);
        return digits;
    }

    private void logRawPayload(Map<String, Object> payload) {
        try {
            log.info("[WEBHOOK-PUB-RAW] Payload:\n{}",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.info("[WEBHOOK-PUB-RAW] Payload (sin formatear): {}", payload);
        }
    }
}
