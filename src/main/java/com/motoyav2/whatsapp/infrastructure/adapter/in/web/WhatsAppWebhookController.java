package com.motoyav2.whatsapp.infrastructure.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motoyav2.debug.DebugWaService;
import com.motoyav2.notifications.infrastructure.adapter.in.web.FactilizaPayloadParser;
import com.motoyav2.notifications.infrastructure.adapter.in.web.FactilizaPayloadParser.MediaEntrante;
import com.motoyav2.notifications.infrastructure.adapter.in.web.FactilizaPayloadParser.TextoEntrante;
import com.motoyav2.notifications.infrastructure.adapter.in.web.MetaPayloadParser;
import com.motoyav2.notifications.infrastructure.adapter.in.web.MetaPayloadParser.InboundMessage;
import com.motoyav2.notifications.infrastructure.adapter.in.web.MetaPayloadParser.StatusUpdate;
import com.motoyav2.notifications.infrastructure.adapter.out.storage.WhatsAppMediaStorageService;
import com.motoyav2.notifications.infrastructure.channel.whatsapp.MetaWhatsAppNotificationAdapter;
import com.motoyav2.notifications.infrastructure.channel.whatsapp.MetaWhatsAppProperties;
import com.motoyav2.whatsapp.domain.event.EstadoMensajeActualizadoWaEvent;
import com.motoyav2.notifications.domain.port.in.WhatsAppMessageDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controller unificado para todos los webhooks de WhatsApp.
 *
 * Rutas activas:
 *   GET  /webhook/meta                                → verificación Meta (hub.challenge)
 *   POST /webhook/meta                                → mensajes entrantes Meta Cloud API
 *   POST /api/v1/notificaciones/whatsapp/webhook      → webhook Factiliza (alias)
 *   POST /webhook/whatsapp                            → webhook Factiliza público (alias)
 *
 * Sustituye:
 *   MetaWebhookController, WhatsAppWebhookController, WhatsAppWebhookPublicoController
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "WhatsApp Webhook", description = "Recepción unificada de mensajes entrantes de WhatsApp")
public class WhatsAppWebhookController {

    private static final Set<String> MEDIA_TYPES = Set.of("image", "document", "audio", "video", "sticker", "ptt");

    private final WhatsAppMessageDispatcher       dispatcher;
    private final MetaPayloadParser               metaParser;
    private final FactilizaPayloadParser          factilizaParser;
    private final WhatsAppMediaStorageService     mediaStorage;
    private final MetaWhatsAppNotificationAdapter metaAdapter;
    private final MetaWhatsAppProperties          metaProperties;
    private final ApplicationEventPublisher       eventPublisher;
    private final ObjectMapper                    objectMapper;
    private final DebugWaService                  debugWaService;

    @Value("${notifications.webhook.debug-payload:false}")
    private boolean debugPayload;

    @Value("${notifications.webhook.token:}")
    private String webhookToken;

    // ─── GET: verificación Meta ───────────────────────────────────────────────

    @GetMapping("/webhook/meta")
    @Operation(summary = "Verificación del webhook Meta")
    public ResponseEntity<String> verifyMeta(
            @RequestParam(name = "hub.mode",         required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge",    required = false) String challenge) {

        String expected = metaProperties.getWebhookVerifyToken();
        if ("subscribe".equals(mode) && expected != null && expected.equals(token)) {
            log.info("[WA-WEBHOOK] Verificación Meta exitosa");
            return ResponseEntity.ok(challenge);
        }
        log.warn("[WA-WEBHOOK] Verificación Meta fallida | mode={}", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
    }

    // ─── POST: Meta Cloud API ─────────────────────────────────────────────────

    @PostMapping("/webhook/meta")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Mensajes entrantes Meta WhatsApp Cloud API")
    public Mono<Map<String, String>> receiveMeta(@RequestBody Map<String, Object> payload) {
        if (debugPayload) logRaw("[META-RAW]", payload);

        // Guardar en debug_wa_mensajes para Tab 2 del wa-debug console
        debugWaService.guardarPayload(payload).subscribe();

        List<InboundMessage> messages = metaParser.extractMessages(payload);
        for (InboundMessage msg : messages) {
            if (msg.text() != null) {
                log.info("[WA-WEBHOOK] Texto Meta | from={} id={}", msg.from(), msg.messageId());
                dispatcher.dispatch(msg.from(), msg.text(), null, null)
                        .subscribe(null, ex -> log.warn("[WA-WEBHOOK] Error dispatch texto: {}", ex.getMessage()));

            } else if (msg.buttonPayload() != null) {
                log.info("[WA-WEBHOOK] Button reply Meta | from={} payload={}", msg.from(), msg.buttonPayload());
                dispatcher.dispatch(msg.from(), msg.buttonPayload(), null, null)
                        .subscribe(null, ex -> log.warn("[WA-WEBHOOK] Error dispatch button: {}", ex.getMessage()));

            } else if (msg.mediaId() != null && msg.mediaType() != null) {
                String effectiveMime = msg.mimeType() != null ? msg.mimeType() : msg.mediaType();
                log.info("[WA-WEBHOOK] Media Meta | from={} type={} mime={}", msg.from(), msg.mediaType(), effectiveMime);
                metaAdapter.downloadMedia(msg.mediaId())
                        .flatMap(bytes -> {
                            String base64 = Base64.getEncoder().encodeToString(bytes);
                            return mediaStorage.subirBase64(base64, effectiveMime, null, null);
                        })
                        .flatMap(url -> dispatcher.dispatch(msg.from(), null, msg.mediaType(), url))
                        .subscribe(null, ex -> log.warn("[WA-WEBHOOK] Error media Meta from={}: {}", msg.from(), ex.getMessage()));
            }
        }

        List<StatusUpdate> statuses = metaParser.extractStatuses(payload);
        for (StatusUpdate s : statuses) {
            if ("failed".equals(s.status())) {
                if (Integer.valueOf(131047).equals(s.errorCode())) {
                    log.warn("[WA-WEBHOOK] ⛔ VENTANA 24H CERRADA | wamid={} phone={} — el destinatario no ha escrito en las últimas 24h. Usa plantilla aprobada en lugar de texto libre.",
                            s.messageId(), s.recipientPhone());
                } else {
                    log.warn("[WA-WEBHOOK] ❌ Entrega FALLIDA | wamid={} phone={} errorCode={} errorTitle={}",
                            s.messageId(), s.recipientPhone(), s.errorCode(), s.errorTitle());
                }
            } else {
                log.info("[WA-WEBHOOK] Status Meta | wamid={} status={} phone={}", s.messageId(), s.status(), s.recipientPhone());
            }
            eventPublisher.publishEvent(new EstadoMensajeActualizadoWaEvent(
                    s.messageId(), s.status(), System.currentTimeMillis()));
        }

        return Mono.just(Map.of("status", "received"));
    }

    // ─── POST: Factiliza (ruta 1) ─────────────────────────────────────────────

    @PostMapping("/api/v1/notificaciones/whatsapp/webhook")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Webhook Factiliza — ruta principal")
    public Mono<Map<String, String>> receiveFactiliza(@RequestBody Map<String, Object> payload) {
        if (debugPayload) logRaw("[FACTILIZA-RAW]", payload);

        factilizaParser.parseText(payload).ifPresentOrElse(
            (TextoEntrante msg) -> {
                log.info("[WA-WEBHOOK] Texto Factiliza | from={}", msg.from());
                dispatcher.dispatch(msg.from(), msg.texto(), null, null)
                        .subscribe(null, ex -> log.warn("[WA-WEBHOOK] Error dispatch Factiliza: {}", ex.getMessage()));
            },
            () -> factilizaParser.parseMedia(payload).ifPresent((MediaEntrante msg) -> {
                log.info("[WA-WEBHOOK] Media Factiliza | from={} type={}", msg.from(), msg.mediaType());
                dispatcher.dispatch(msg.from(), null, msg.mediaType(), msg.mediaUrl())
                        .subscribe(null, ex -> log.warn("[WA-WEBHOOK] Error dispatch media Factiliza: {}", ex.getMessage()));
            })
        );

        return Mono.just(Map.of("status", "received"));
    }

    // ─── POST: Factiliza público (ruta 2) ─────────────────────────────────────

    @PostMapping("/webhook/whatsapp")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Webhook Factiliza público — ruta alternativa")
    public Mono<Map<String, String>> receiveFactilizaPublico(
            @RequestParam(name = "token", required = false) String tokenParam,
            @RequestBody Map<String, Object> payload) {

        if (webhookToken != null && !webhookToken.isBlank() && !webhookToken.equals(tokenParam)) {
            log.warn("[WA-WEBHOOK] Token inválido — ignorado");
            return Mono.just(Map.of("status", "received"));
        }
        if (debugPayload) logRaw("[FACTILIZA-PUB-RAW]", payload);

        String eventType = extractEventType(payload);
        if (isStatusEvent(eventType)) {
            log.debug("[WA-WEBHOOK] Evento de estado ignorado: {}", eventType);
            return Mono.just(Map.of("status", "received"));
        }

        String from = extractFrom(payload);
        if (from == null || from.isBlank()) {
            log.warn("[WA-WEBHOOK] Sin 'from' reconocible — ignorado");
            return Mono.just(Map.of("status", "received"));
        }

        String text      = extractText(payload);
        String mediaType = extractMediaType(payload);
        String base64    = extractBase64(payload);
        String filename  = extractFilename(payload);

        if (text != null) {
            dispatcher.dispatch(from, text, null, null)
                    .subscribe(null, ex -> log.warn("[WA-WEBHOOK] Error dispatch público: {}", ex.getMessage()));

        } else if (base64 != null && mediaType != null) {
            mediaStorage.subirBase64(base64, mediaType, filename, null)
                    .flatMap(url -> dispatcher.dispatch(from, null, mediaType, url))
                    .subscribe(null, ex -> log.warn("[WA-WEBHOOK] Error media público: {}", ex.getMessage()));

        } else {
            log.warn("[WA-WEBHOOK] Sin contenido reconocible | from={} type={}", from, eventType);
        }

        return Mono.just(Map.of("status", "received"));
    }

    // ─── Helpers Factiliza público ────────────────────────────────────────────

    private String extractEventType(Map<String, Object> p) {
        Object t = p.get("type");
        if (t instanceof String s) return s.toLowerCase();
        Object ev = p.get("event");
        if (ev instanceof String s) return s.toLowerCase();
        if (p.get("data") instanceof Map<?, ?> d) {
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
            if (p.get("from")   instanceof String s && !s.isBlank()) return cleanPhone(s);
            if (p.get("number") instanceof String s && !s.isBlank()) return cleanPhone(s);
            if (p.get("phone")  instanceof String s && !s.isBlank()) return cleanPhone(s);
            if (p.get("data") instanceof Map<?, ?> d) {
                if (d.get("from") instanceof String s && !s.isBlank()) return cleanPhone(s);
                if (d.get("key")  instanceof Map<?, ?> k && k.get("remoteJid") instanceof String s) return cleanPhone(s);
            }
            return null;
        } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> p) {
        try {
            if (Boolean.TRUE.equals(p.get("isMedia"))) return null;
            String type = p.get("type") instanceof String t ? t.toLowerCase() : "";
            if (MEDIA_TYPES.contains(type)) return null;
            if (p.get("body") instanceof String b && !b.isBlank() && !looksLikeBase64(b)) return b;
            if (p.get("text") instanceof String t && !t.isBlank()) return t;
            if (p.get("message") instanceof Map<?, ?> msg) {
                if (msg.get("body")         instanceof String s && !s.isBlank()) return s;
                if (msg.get("text")         instanceof String s && !s.isBlank()) return s;
                if (msg.get("conversation") instanceof String s && !s.isBlank()) return s;
                if (msg.get("extendedTextMessage") instanceof Map<?, ?> em && em.get("text") instanceof String s) return s;
            }
            if (p.get("data") instanceof Map<?, ?> d) {
                if (d.get("body") instanceof String s && !s.isBlank() && !looksLikeBase64(s)) return s;
                if (d.get("message") instanceof Map<?, ?> dm) {
                    if (dm.get("conversation") instanceof String s) return s;
                    if (dm.get("extendedTextMessage") instanceof Map<?, ?> em && em.get("text") instanceof String s) return s;
                }
            }
            return null;
        } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private String extractMediaType(Map<String, Object> p) {
        try {
            if (Boolean.TRUE.equals(p.get("isMedia"))) {
                return p.get("type") instanceof String t ? t.toLowerCase() : "document";
            }
            String type = p.get("type") instanceof String t ? t.toLowerCase() : null;
            if (type != null && MEDIA_TYPES.contains(type)) return type;
            if (p.get("body") instanceof String b && looksLikeBase64(b)) return "document";
            if (p.get("data") instanceof Map<?, ?> d && d.get("type") instanceof String dt) {
                String lower = dt.toLowerCase();
                if (MEDIA_TYPES.contains(lower)) return lower;
            }
            return null;
        } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private String extractBase64(Map<String, Object> p) {
        try {
            if (p.get("body")   instanceof String b && looksLikeBase64(b)) return b;
            if (p.get("data64") instanceof String d) return d;
            if (p.get("base64") instanceof String b) return b;
            if (p.get("data") instanceof Map<?, ?> d) {
                if (d.get("body")   instanceof String s && looksLikeBase64(s)) return s;
                if (d.get("base64") instanceof String s) return s;
            }
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

    private boolean looksLikeBase64(String s) {
        if (s == null || s.length() < 100) return false;
        if (s.startsWith("data:"))         return true;
        long valid = s.chars().filter(c -> Character.isLetterOrDigit(c) || c == '+' || c == '/' || c == '=').count();
        return (double) valid / s.length() > 0.9;
    }

    private String cleanPhone(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("@.*$", "").replaceAll("[^0-9]", "");
        if (digits.startsWith("51") && digits.length() == 11) return digits.substring(2);
        return digits;
    }

    private void logRaw(String prefix, Map<String, Object> payload) {
        try {
            log.info("{}\n{}", prefix, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.info("{} {}", prefix, payload);
        }
    }
}
