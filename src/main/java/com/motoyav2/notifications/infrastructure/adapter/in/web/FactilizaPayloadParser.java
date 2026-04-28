package com.motoyav2.notifications.infrastructure.adapter.in.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Parsea payloads entrantes del webhook de Factiliza.
 *
 * Soporta múltiples formatos de entrega de media (resolución en cascada):
 *   1. url   → URL directa descargable
 *   2. link  → alias de url (algunos proveedores lo usan)
 *   3. id    → mediaId de Factiliza → construye factiliza://media/{id}
 *              (WhatsAppMediaStorageService.subirDesdeUrl lo descarga vía factilizaWebClient)
 *   4. data  → base64 inline → construye data:{mime};base64,{data}
 *              (WhatsAppMediaStorageService.subirDesdeUrl lo decodifica directamente)
 */
@Slf4j
@Component
public class FactilizaPayloadParser {

    private static final Map<String, String> MIME_MAP = Map.of(
            "image",    "image/jpeg",
            "document", "application/pdf",
            "audio",    "audio/ogg",
            "video",    "video/mp4"
    );

    public record TextoEntrante(String from, String texto) {}
    public record MediaEntrante(String from, String mediaType, String mediaUrl) {}

    @SuppressWarnings("unchecked")
    public Optional<TextoEntrante> parseText(Map<String, Object> payload) {
        try {
            String from      = (String) payload.get("from");
            Map<?, ?> msgObj = (Map<?, ?>) payload.get("message");
            if (from == null || msgObj == null) return Optional.empty();
            if (!"text".equals(msgObj.get("type"))) return Optional.empty();
            Map<?, ?> textObj = (Map<?, ?>) msgObj.get("text");
            if (textObj == null) return Optional.empty();
            String body = (String) textObj.get("body");
            if (body == null || body.isBlank()) return Optional.empty();
            return Optional.of(new TextoEntrante(from, body));
        } catch (Exception e) {
            log.warn("[FACTILIZA-PARSER] Error parseando texto: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<MediaEntrante> parseMedia(Map<String, Object> payload) {
        try {
            String from      = (String) payload.get("from");
            Map<?, ?> msgObj = (Map<?, ?>) payload.get("message");
            if (from == null || msgObj == null) return Optional.empty();
            String type = (String) msgObj.get("type");
            if (type == null || "text".equals(type)) return Optional.empty();

            Map<?, ?> mediaObj = (Map<?, ?>) msgObj.get(type);
            String url = resolveMediaUrl(type, mediaObj);

            if (url == null) {
                log.warn("[FACTILIZA-PARSER] Media recibida sin URL resoluble | " +
                         "type={} from={} camposDisponibles={}",
                         type, from, mediaObj != null ? mediaObj.keySet() : "null");
                return Optional.empty();
            }
            return Optional.of(new MediaEntrante(from, type, url));
        } catch (Exception e) {
            log.warn("[FACTILIZA-PARSER] Error parseando media: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolución en cascada del URL del media.
     * Cada estrategia se intenta en orden; se retorna la primera que resuelve.
     */
    private String resolveMediaUrl(String mediaType, Map<?, ?> mediaObj) {
        if (mediaObj == null) return null;

        // 1. Campo "url" — URL directa
        Object url = mediaObj.get("url");
        if (url instanceof String s && !s.isBlank()) return s;

        // 2. Campo "link" — alias usado por algunas versiones del API
        Object link = mediaObj.get("link");
        if (link instanceof String s && !s.isBlank()) return s;

        // 3. Campo "id" — mediaId de Factiliza, descargado por factilizaWebClient
        Object id = mediaObj.get("id");
        if (id instanceof String s && !s.isBlank()) {
            log.debug("[FACTILIZA-PARSER] Resolviendo mediaId={} → factiliza://media/{}", s, s);
            return "factiliza://media/" + s;
        }

        // 4. Campo "data" — base64 inline (menos común, pero soportado)
        Object data = mediaObj.get("data");
        if (data instanceof String s && !s.isBlank()) {
            String mime = MIME_MAP.getOrDefault(
                    mediaType != null ? mediaType.toLowerCase() : "", "application/octet-stream");
            log.debug("[FACTILIZA-PARSER] Resolviendo base64 inline | mime={}", mime);
            return "data:" + mime + ";base64," + s;
        }

        return null;
    }
}
