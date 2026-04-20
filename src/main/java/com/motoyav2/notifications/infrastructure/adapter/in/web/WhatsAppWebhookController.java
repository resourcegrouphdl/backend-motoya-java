package com.motoyav2.notifications.infrastructure.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motoyav2.notifications.domain.port.in.WhatsAppMessageDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Endpoint para recibir mensajes entrantes de WhatsApp vía Factiliza.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ESTADO: LISTO — pendiente de configurar el webhook en el panel de Factiliza.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Registro en Factiliza:
 *   Panel Factiliza → Instancia → Webhook URL:
 *   https://{CLOUD_RUN_URL}/api/v1/notificaciones/whatsapp/webhook
 *
 * Modo debug (notifications.webhook.debug-payload=true):
 *   Loguea el payload completo en INFO para identificar la estructura real
 *   que envía Factiliza. Activar durante las primeras pruebas, luego desactivar.
 *
 * Flujo de mensajes entrantes:
 *   Cliente responde por WhatsApp
 *   → Factiliza hace POST a este endpoint
 *   → Se loguea el payload raw (si debug=true)
 *   → Se intenta parsear el mensaje de texto
 *   → ProcesarRespuestaReferenciaUseCase procesa la respuesta
 *   → TODO: si imagen/PDF → emitir PAYMENT_PROOF_RECEIVED
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notificaciones/whatsapp")
@Tag(name = "WhatsApp Webhook", description = "Recepción de mensajes entrantes de WhatsApp vía Factiliza")
public class WhatsAppWebhookController {

    private final WhatsAppMessageDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    /**
     * Activar durante las primeras pruebas para ver el payload real de Factiliza.
     * Loguea el JSON completo en nivel INFO → visible en Cloud Run Logs.
     * Desactivar en producción estable.
     */
    @Value("${notifications.webhook.debug-payload:true}")
    private boolean debugPayload;

    // ─── Endpoint principal ───────────────────────────────────────────────────

    /**
     * Recepción de mensajes entrantes de WhatsApp desde Factiliza.
     *
     * Siempre responde HTTP 200 inmediatamente para que Factiliza no reintente.
     * El procesamiento ocurre en background (fire-and-forget).
     */
    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Recibir mensaje entrante WhatsApp",
            description = "Webhook Factiliza. Loguea el payload raw (modo debug) y procesa respuestas de referencias.")
    public Mono<Map<String, String>> receiveMessage(@RequestBody Map<String, Object> payload) {

        // ── 1. Log del payload raw — crítico para ajustar el parsing ──────────
        if (debugPayload) {
            logRawPayload(payload);
        }

        // ── 2. Despachar mediante el dispatcher central ───────────────────────
        var textMsg  = extractTextMessage(payload);
        var mediaMsg = extractMediaMessage(payload);

        if (textMsg.isPresent()) {
            var msg = textMsg.get();
            log.info("[WEBHOOK] ✓ Mensaje de texto | from={}", msg.from());
            dispatcher.dispatch(msg.from(), msg.text(), null, null)
                    .subscribe(null, ex -> log.warn("[WEBHOOK] Error en dispatch: {}", ex.getMessage()));
        } else if (mediaMsg.isPresent()) {
            var msg = mediaMsg.get();
            log.info("[WEBHOOK] ✓ Mensaje de media | from={} type={}", msg.from(), msg.mediaType());
            dispatcher.dispatch(msg.from(), null, msg.mediaType(), msg.mediaUrl())
                    .subscribe(null, ex -> log.warn("[WEBHOOK] Error en dispatch media: {}", ex.getMessage()));
        }

        // ── 3. Responder 200 siempre ──────────────────────────────────────────
        return Mono.just(Map.of("status", "received"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Loguea el payload completo como JSON formateado.
     * Usar durante las primeras pruebas para identificar la estructura real
     * que envía Factiliza y ajustar extractTextMessage() si es necesario.
     *
     * Buscar en Cloud Run Logs:
     *   [WEBHOOK-RAW] Payload recibido de Factiliza
     */
    private void logRawPayload(Map<String, Object> payload) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            log.info("[WEBHOOK-RAW] Payload recibido de Factiliza:\n{}", json);
        } catch (JsonProcessingException e) {
            log.info("[WEBHOOK-RAW] Payload recibido (sin formatear): {}", payload);
        }
    }

    /**
     * Extrae el mensaje de texto del payload de Factiliza.
     *
     * NOTA: estructura asumida — ajustar según el payload real logueado
     * con [WEBHOOK-RAW] durante las primeras pruebas.
     *
     * Estructura asumida:
     * {
     *   "from": "51999999999",
     *   "message": {
     *     "type": "text",
     *     "text": { "body": "Sí confirmo" }
     *   }
     * }
     */
    record IncomingMessage(String from, String text) {}
    record IncomingMedia(String from, String mediaType, String mediaUrl) {}

    @SuppressWarnings("unchecked")
    private java.util.Optional<IncomingMessage> extractTextMessage(Map<String, Object> payload) {
        try {
            String from      = (String) payload.get("from");
            Map<?, ?> msgObj = (Map<?, ?>) payload.get("message");
            if (from == null || msgObj == null) return java.util.Optional.empty();
            String type = (String) msgObj.get("type");
            if (!"text".equals(type)) return java.util.Optional.empty();
            Map<?, ?> textObj = (Map<?, ?>) msgObj.get("text");
            if (textObj == null) return java.util.Optional.empty();
            String body = (String) textObj.get("body");
            if (body == null || body.isBlank()) return java.util.Optional.empty();
            return java.util.Optional.of(new IncomingMessage(from, body));
        } catch (Exception e) {
            log.warn("[WEBHOOK] Error parseando text message: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.Optional<IncomingMedia> extractMediaMessage(Map<String, Object> payload) {
        try {
            String from      = (String) payload.get("from");
            Map<?, ?> msgObj = (Map<?, ?>) payload.get("message");
            if (from == null || msgObj == null) return java.util.Optional.empty();
            String type = (String) msgObj.get("type");
            if (type == null || "text".equals(type)) return java.util.Optional.empty();
            Map<?, ?> mediaObj = (Map<?, ?>) msgObj.get(type);
            String url = null;
            if (mediaObj != null) {
                Object urlObj = mediaObj.get("url");
                if (urlObj == null) urlObj = mediaObj.get("link");
                if (urlObj instanceof String s) url = s;
            }
            return java.util.Optional.of(new IncomingMedia(from, type, url));
        } catch (Exception e) {
            log.warn("[WEBHOOK] Error parseando media message: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
