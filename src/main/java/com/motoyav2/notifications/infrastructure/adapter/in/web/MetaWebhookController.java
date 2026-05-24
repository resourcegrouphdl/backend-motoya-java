package com.motoyav2.notifications.infrastructure.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motoyav2.notifications.domain.port.in.WhatsAppMessageDispatcher;
import com.motoyav2.notifications.infrastructure.adapter.in.web.MetaPayloadParser.InboundMessage;
import com.motoyav2.notifications.infrastructure.adapter.in.web.MetaPayloadParser.StatusUpdate;
import com.motoyav2.notifications.infrastructure.adapter.out.storage.WhatsAppMediaStorageService;
import com.motoyav2.notifications.infrastructure.channel.whatsapp.MetaWhatsAppNotificationAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Webhook para Meta WhatsApp Cloud API.
 *
 * Configurar en Meta Business Manager → WhatsApp → Configuración → Webhooks:
 *   URL de callback:    https://{CLOUD_RUN_URL}/webhook/meta
 *   Token de verificación: valor de META_WA_WEBHOOK_VERIFY_TOKEN
 *   Campos suscritos:   messages
 *
 * Maneja:
 *   GET  /webhook/meta → verificación del webhook por Meta
 *   POST /webhook/meta → mensajes entrantes y actualizaciones de estado
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/webhook/meta")
@Tag(name = "Meta Webhook", description = "Recepción de mensajes entrantes de WhatsApp vía Meta Cloud API")
public class MetaWebhookController {

    private final WhatsAppMessageDispatcher      dispatcher;
    private final WhatsAppMediaStorageService    mediaStorage;
    private final MetaWhatsAppNotificationAdapter metaAdapter;
    private final MetaPayloadParser              parser;
    private final ObjectMapper                   objectMapper;

    @Value("${notifications.meta.webhook-verify-token:}")
    private String verifyToken;

    @Value("${notifications.webhook.debug-payload:false}")
    private boolean debugPayload;

    // ─── GET: verificación del webhook ───────────────────────────────────────

    @GetMapping
    @Operation(summary = "Verificación del webhook Meta")
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode",         required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge",    required = false) String challenge) {

        if ("subscribe".equals(mode) && verifyToken != null && verifyToken.equals(token)) {
            log.info("[META-WEBHOOK] Verificación exitosa");
            return ResponseEntity.ok(challenge);
        }
        log.warn("[META-WEBHOOK] Verificación fallida | mode={} token_match={}", mode, verifyToken.equals(token));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
    }

    // ─── POST: mensajes entrantes y status ───────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Recibir mensajes e updates de Meta WhatsApp")
    public Mono<Map<String, String>> receive(@RequestBody Map<String, Object> payload) {

        if (debugPayload) logRaw(payload);

        // ── Mensajes entrantes ────────────────────────────────────────────────
        List<InboundMessage> messages = parser.extractMessages(payload);
        for (InboundMessage msg : messages) {
            if (msg.text() != null) {
                log.info("[META-WEBHOOK] Texto entrante | from={} id={}", msg.from(), msg.messageId());
                dispatcher.dispatch(msg.from(), msg.text(), null, null)
                        .subscribe(null, ex -> log.warn("[META-WEBHOOK] Error dispatch texto: {}", ex.getMessage()));

            } else if (msg.buttonPayload() != null) {
                // Respuesta de botón quick_reply — se trata como texto estructurado
                log.info("[META-WEBHOOK] Button reply | from={} payload={}", msg.from(), msg.buttonPayload());
                dispatcher.dispatch(msg.from(), msg.buttonPayload(), null, null)
                        .subscribe(null, ex -> log.warn("[META-WEBHOOK] Error dispatch button: {}", ex.getMessage()));

            } else if (msg.mediaId() != null && msg.mediaType() != null) {
                log.info("[META-WEBHOOK] Media entrante | from={} type={} id={}", msg.from(), msg.mediaType(), msg.mediaId());
                metaAdapter.downloadMedia(msg.mediaId())
                        .flatMap(bytes -> {
                            String base64 = Base64.getEncoder().encodeToString(bytes);
                            return mediaStorage.subirBase64(base64, msg.mediaType(), null, null);
                        })
                        .flatMap(url -> dispatcher.dispatch(msg.from(), null, msg.mediaType(), url))
                        .subscribe(null, ex -> log.warn("[META-WEBHOOK] Error procesando media from={}: {}", msg.from(), ex.getMessage()));
            }
        }

        // ── Status updates (sent/delivered/read/failed) ───────────────────────
        List<StatusUpdate> statuses = parser.extractStatuses(payload);
        for (StatusUpdate s : statuses) {
            log.info("[META-WEBHOOK] Status | wamid={} status={} to={}", s.messageId(), s.status(), s.recipientPhone());
        }

        return Mono.just(Map.of("status", "received"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void logRaw(Map<String, Object> payload) {
        try {
            log.info("[META-WEBHOOK-RAW]\n{}",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.info("[META-WEBHOOK-RAW] {}", payload);
        }
    }
}