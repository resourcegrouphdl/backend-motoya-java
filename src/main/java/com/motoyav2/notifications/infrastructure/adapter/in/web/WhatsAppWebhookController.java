package com.motoyav2.notifications.infrastructure.adapter.in.web;

import com.motoyav2.evaluacion.domain.port.in.ProcesarRespuestaReferenciaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Endpoint stub para recibir mensajes entrantes de WhatsApp Business (Meta Cloud API).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ESTADO: STUB — activar cuando el número de WhatsApp Business esté configurado.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Registro en Meta:
 *   1. Portal Meta for Developers → App → WhatsApp → Configuración
 *   2. Webhook URL: https://{CLOUD_RUN_URL}/api/v1/notificaciones/whatsapp/webhook
 *   3. Token de verificación: ${notifications.meta.webhook-verify-token}
 *   4. Suscribirse a: messages
 *
 * Flujo de mensajes entrantes (a implementar en Fase 2 — Firebase Functions):
 *   Cliente envía mensaje/imagen → Meta envía POST a este endpoint
 *   → Backend registra en Firestore (whatsapp_messages)
 *   → Si imagen/PDF → Firebase Function procesa con Document AI
 *   → Emite evento PAYMENT_PROOF_RECEIVED
 *
 * Este endpoint existe aquí como fallback. La implementación principal
 * será en Firebase Functions para evitar timeouts y aprovechar triggers de Firestore.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notificaciones/whatsapp")
@Tag(name = "WhatsApp Webhook", description = "Recepción de mensajes entrantes de WhatsApp Business")
public class WhatsAppWebhookController {

    @Value("${notifications.meta.webhook-verify-token:changeme}")
    private String webhookVerifyToken;

    private final ProcesarRespuestaReferenciaUseCase procesarRespuestaReferencia;

    /**
     * Verificación del webhook por Meta.
     * Meta envía GET con hub.challenge al registrar el webhook.
     * Este endpoint debe responder con el valor de hub.challenge.
     */
    @GetMapping("/webhook")
    @Operation(
            summary = "Verificar webhook Meta",
            description = "Verificación inicial de Meta al registrar el webhook. Retorna hub.challenge.")
    public Mono<ResponseEntity<String>> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {

        log.info("[WHATSAPP-WEBHOOK] Verificación de Meta | mode={}", mode);

        if ("subscribe".equals(mode) && webhookVerifyToken.equals(verifyToken)) {
            log.info("[WHATSAPP-WEBHOOK] ✓ Webhook verificado correctamente");
            return Mono.just(ResponseEntity.ok(challenge));
        }

        log.warn("[WHATSAPP-WEBHOOK] ✗ Token de verificación incorrecto");
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Token inválido"));
    }

    /**
     * Recepción de mensajes entrantes de WhatsApp.
     *
     * STUB: actualmente solo registra el payload en logs.
     * Implementación completa en Fase 2 (Firebase Functions).
     *
     * Meta espera respuesta HTTP 200 en menos de 20 segundos,
     * de lo contrario reintenta el envío.
     */
    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Recibir mensaje entrante WhatsApp",
               description = "Webhook Meta Cloud API. Procesa respuestas de referencias y vouchers de pago.")
    public Mono<Map<String, String>> receiveMessage(@RequestBody Map<String, Object> payload) {
        // Meta espera 200 en < 20s — procesamos en background y respondemos de inmediato
        extractTextMessage(payload).ifPresent(msg -> {
            log.info("[WEBHOOK] Mensaje entrante | from={} text={}", msg.from(), msg.text());
            // Procesamiento de referencias: fire-and-forget
            procesarRespuestaReferencia.ejecutar(msg.from(), msg.text())
                    .subscribe(
                            null,
                            ex -> log.debug("[WEBHOOK] No era respuesta de referencia o error: {}", ex.getMessage())
                    );
            // TODO: para vouchers de pago emitir PAYMENT_PROOF_RECEIVED en notification_events
        });
        return Mono.just(Map.of("status", "received"));
    }

    // ── Helpers de parsing del payload Meta ───────────────────────────────────

    record IncomingMessage(String from, String text) {}

    @SuppressWarnings("unchecked")
    private java.util.Optional<IncomingMessage> extractTextMessage(Map<String, Object> payload) {
        try {
            List<?> entries = (List<?>) payload.get("entry");
            if (entries == null || entries.isEmpty()) return java.util.Optional.empty();

            Map<?, ?> entry   = (Map<?, ?>) entries.get(0);
            List<?> changes   = (List<?>) entry.get("changes");
            if (changes == null || changes.isEmpty()) return java.util.Optional.empty();

            Map<?, ?> value   = (Map<?, ?>) ((Map<?, ?>) changes.get(0)).get("value");
            if (value == null) return java.util.Optional.empty();

            List<?> messages  = (List<?>) value.get("messages");
            if (messages == null || messages.isEmpty()) return java.util.Optional.empty();

            Map<?, ?> msg = (Map<?, ?>) messages.get(0);
            String type   = (String) msg.get("type");
            if (!"text".equals(type)) return java.util.Optional.empty();

            String from   = (String) msg.get("from");
            String text   = (String) ((Map<?, ?>) msg.get("text")).get("body");
            if (from == null || text == null || text.isBlank()) return java.util.Optional.empty();

            return java.util.Optional.of(new IncomingMessage(from, text));
        } catch (Exception e) {
            log.warn("[WEBHOOK] Error parseando payload Meta: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
