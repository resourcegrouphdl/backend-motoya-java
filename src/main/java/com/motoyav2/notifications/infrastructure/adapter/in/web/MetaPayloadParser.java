package com.motoyav2.notifications.infrastructure.adapter.in.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parsea el payload del webhook de Meta WhatsApp Cloud API.
 *
 * Estructura del payload de Meta:
 * {
 *   "object": "whatsapp_business_account",
 *   "entry": [{
 *     "changes": [{
 *       "value": {
 *         "messages":  [...],   ← mensajes entrantes
 *         "statuses":  [...]    ← actualizaciones de entrega
 *       }
 *     }]
 *   }]
 * }
 */
@Slf4j
@Component
public class MetaPayloadParser {

    public record InboundMessage(
            String from,
            String messageId,
            String text,
            String mediaType,
            String mediaId,
            String buttonPayload) {}

    public record StatusUpdate(
            String messageId,
            String status,
            String recipientPhone) {}

    @SuppressWarnings("unchecked")
    public List<InboundMessage> extractMessages(Map<String, Object> payload) {
        List<InboundMessage> result = new ArrayList<>();
        try {
            for (var entry : getEntries(payload)) {
                for (var change : getChanges(entry)) {
                    Object value = change.get("value");
                    if (!(value instanceof Map<?, ?> valueMap)) continue;
                    Object messages = valueMap.get("messages");
                    if (!(messages instanceof List<?> msgList)) continue;
                    for (var rawMsg : msgList) {
                        if (!(rawMsg instanceof Map<?, ?> msg)) continue;
                        InboundMessage parsed = parseMessage((Map<String, Object>) msg);
                        if (parsed != null) result.add(parsed);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[META-PARSER] Error extrayendo mensajes: {}", e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<StatusUpdate> extractStatuses(Map<String, Object> payload) {
        List<StatusUpdate> result = new ArrayList<>();
        try {
            for (var entry : getEntries(payload)) {
                for (var change : getChanges(entry)) {
                    Object value = change.get("value");
                    if (!(value instanceof Map<?, ?> valueMap)) continue;
                    Object statuses = valueMap.get("statuses");
                    if (!(statuses instanceof List<?> statusList)) continue;
                    for (var rawStatus : statusList) {
                        if (!(rawStatus instanceof Map<?, ?> s)) continue;
                        String id          = getString(s, "id");
                        String status      = getString(s, "status");
                        String recipientId = getString(s, "recipient_id");
                        if (id != null && status != null) {
                            result.add(new StatusUpdate(id, status, recipientId));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[META-PARSER] Error extrayendo statuses: {}", e.getMessage());
        }
        return result;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private InboundMessage parseMessage(Map<String, Object> msg) {
        String from      = normalizePhone(getString(msg, "from"));
        String messageId = getString(msg, "id");
        String type      = getString(msg, "type");
        if (from == null || type == null) return null;

        return switch (type) {
            case "text" -> {
                Object textObj = msg.get("text");
                String body = (textObj instanceof Map<?, ?> t) ? getString(t, "body") : null;
                yield body != null ? new InboundMessage(from, messageId, body, null, null, null) : null;
            }
            case "image", "document", "audio", "video", "sticker" -> {
                Object mediaObj = msg.get(type);
                String mediaId = (mediaObj instanceof Map<?, ?> m) ? getString(m, "id") : null;
                yield new InboundMessage(from, messageId, null, type, mediaId, null);
            }
            case "button" -> {
                Object btnObj = msg.get("button");
                String payload = (btnObj instanceof Map<?, ?> b) ? getString(b, "payload") : null;
                String text    = (btnObj instanceof Map<?, ?> b) ? getString(b, "text") : null;
                yield new InboundMessage(from, messageId, text, null, null, payload);
            }
            case "interactive" -> {
                Object intObj = msg.get("interactive");
                if (!(intObj instanceof Map<?, ?> inter)) yield null;
                String interType = getString(inter, "type");
                if ("button_reply".equals(interType)) {
                    Object br = inter.get("button_reply");
                    String payload = (br instanceof Map<?, ?> b) ? getString(b, "id") : null;
                    String title   = (br instanceof Map<?, ?> b) ? getString(b, "title") : null;
                    yield new InboundMessage(from, messageId, title, null, null, payload);
                }
                yield null;
            }
            default -> {
                log.debug("[META-PARSER] Tipo de mensaje no manejado: {}", type);
                yield null;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getEntries(Map<String, Object> payload) {
        Object entries = payload.get("entry");
        if (!(entries instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (var e : list) {
            if (e instanceof Map<?, ?> m) result.add((Map<String, Object>) m);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getChanges(Map<String, Object> entry) {
        Object changes = entry.get("changes");
        if (!(changes instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (var c : list) {
            if (c instanceof Map<?, ?> m) result.add((Map<String, Object>) m);
        }
        return result;
    }

    private String getString(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }

    private String normalizePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) return digits.substring(2);
        return digits;
    }
}