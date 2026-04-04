package com.motoyav2.notifications.infrastructure.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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
@RequestMapping("/api/v1/notificaciones/whatsapp")
@Tag(name = "WhatsApp Webhook", description = "Recepción de mensajes entrantes de WhatsApp Business")
public class WhatsAppWebhookController {

    @Value("${notifications.meta.webhook-verify-token:changeme}")
    private String webhookVerifyToken;

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
    @Operation(
            summary = "Recibir mensaje entrante WhatsApp",
            description = "Webhook para mensajes entrantes de WhatsApp Business. " +
                    "STUB: registra el payload. Implementación completa en Fase 2.")
    public Mono<Map<String, String>> receiveMessage(@RequestBody Map<String, Object> payload) {
        log.info("[WHATSAPP-WEBHOOK] Mensaje entrante recibido | payload_keys={}",
                payload.keySet());

        // TODO Fase 2: implementar en Firebase Function para mejor manejo de media/timeouts
        // Flujo:
        //   1. Extraer messages[0] del payload
        //   2. Si type == "image" o "document":
        //      - Descargar media de Meta API usando media.id
        //      - Upload a Firebase Storage: whatsapp-vouchers/{from}/{timestamp}/{filename}
        //      - Crear documento en colección whatsapp_messages
        //      - Emitir evento PAYMENT_PROOF_RECEIVED en notification_events
        //   3. Si type == "text":
        //      - Crear documento en whatsapp_messages para análisis/seguimiento
        //      - Verificar si es respuesta a un mensaje de cobro activo

        return Mono.just(Map.of("status", "received"));
    }
}
