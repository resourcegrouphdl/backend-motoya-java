package com.motoyav2.notifications.infrastructure.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motoyav2.notifications.domain.port.in.WhatsAppMessageDispatcher;
import com.motoyav2.notifications.infrastructure.adapter.in.web.FactilizaPayloadParser.MediaEntrante;
import com.motoyav2.notifications.infrastructure.adapter.in.web.FactilizaPayloadParser.TextoEntrante;
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
    private final ObjectMapper              objectMapper;
    private final FactilizaPayloadParser    payloadParser;

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
        payloadParser.parseText(payload).ifPresentOrElse(
            (TextoEntrante msg) -> {
                log.info("[WEBHOOK] ✓ Mensaje de texto | from={}", msg.from());
                dispatcher.dispatch(msg.from(), msg.texto(), null, null)
                        .subscribe(null, ex -> log.warn("[WEBHOOK] Error en dispatch: {}", ex.getMessage()));
            },
            () -> payloadParser.parseMedia(payload).ifPresent((MediaEntrante msg) -> {
                log.info("[WEBHOOK] ✓ Mensaje de media | from={} type={}", msg.from(), msg.mediaType());
                dispatcher.dispatch(msg.from(), null, msg.mediaType(), msg.mediaUrl())
                        .subscribe(null, ex -> log.warn("[WEBHOOK] Error en dispatch media: {}", ex.getMessage()));
            })
        );

        // ── 3. Responder 200 siempre ──────────────────────────────────────────
        return Mono.just(Map.of("status", "received"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void logRawPayload(Map<String, Object> payload) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            log.info("[WEBHOOK-RAW] Payload recibido de Factiliza:\n{}", json);
        } catch (JsonProcessingException e) {
            log.info("[WEBHOOK-RAW] Payload recibido (sin formatear): {}", payload);
        }
    }
}
