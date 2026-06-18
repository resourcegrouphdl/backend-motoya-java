// ═══════════════════════════════════════════════════════════════════════════
// OBSOLETO — Reemplazado completamente por MetaWhatsAppNotificationAdapter
// No usar en código nuevo. Pendiente de eliminación cuando FactilizaWaSenderAdapter
// (cobranza/) migre a inyectar MetaWhatsAppNotificationAdapter directamente.
// Ref: MEJORA WA-1 del plan de refactorización.
// ═══════════════════════════════════════════════════════════════════════════
package com.motoyav2.notifications.infrastructure.channel.whatsapp;

import com.motoyav2.notifications.domain.model.Notification;
import com.motoyav2.notifications.domain.model.NotificationChannel;
import com.motoyav2.notifications.domain.ports.out.NotificationSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Adaptador WhatsApp usando la API de json.pe.
 *
 * Endpoints:
 *   Texto : POST /send/text
 *   Media : POST /send/media
 *
 * Configuración requerida:
 *   notifications.factiliza.base-url → https://api.whatsapp.json.pe
 *   notifications.factiliza.token    → Bearer token
 */
// Reemplazado por MetaWhatsAppNotificationAdapter. Mantenido como referencia histórica.
@Slf4j
public class FactilizaWhatsAppNotificationAdapter implements NotificationSenderPort {

    private final WebClient webClient;

    public FactilizaWhatsAppNotificationAdapter(
            @Qualifier("factilizaWhatsAppWebClient") WebClient webClient,
            FactilizaProperties properties) {
        this.webClient = webClient;
    }

    // ─── NotificationSenderPort ───────────────────────────────────────────────

    @Override
    public Mono<String> send(Notification notification) {
        return sendText(notification.recipient(), notification.renderedContent());
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    // ─── Métodos públicos ─────────────────────────────────────────────────────

    /**
     * Envía un mensaje de texto por WhatsApp.
     *
     * @param recipient Número destino (9 dígitos peruanos o con código de país)
     * @param text      Texto del mensaje
     * @return wamid del mensaje enviado, o cadena vacía si no hay data
     */
    public Mono<String> sendText(String recipient, String text) {
        String to = normalizePhone(recipient);

        Map<String, String> body = Map.of(
                "number", to,
                "text", text
        );

        return webClient.post()
                .uri("/send/text")
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(err -> Mono.error(
                                        new FactilizaApiException("WhatsApp API error: " + err))))
                .bodyToMono(JsonPeResponse.class)
                .map(r -> {
                    String wamid = (r != null && r.data() != null) ? r.data().getOrDefault("ID", "").toString() : "";
                    log.info("[WA] ✓ Texto enviado | to={} wamid={}", to, wamid);
                    return wamid;
                })
                .defaultIfEmpty("");
    }

    /**
     * Envía un archivo adjunto por WhatsApp.
     *
     * @param recipient  Número destino
     * @param mediaUrl   URL pública del archivo
     * @param mediatype  Tipo: "image" | "document" | "video" | "audio"
     * @param filename   Nombre del archivo con extensión — puede ser null
     * @param caption    Texto que acompaña al archivo — puede ser null
     * @return wamid del mensaje enviado
     */
    public Mono<String> sendMedia(String recipient, String mediaUrl,
                                   String mediatype, String filename, String caption) {
        String to = normalizePhone(recipient);

        Map<String, Object> body = new HashMap<>();
        body.put("number", to);
        body.put("mediatype", mediatype);
        body.put("media", mediaUrl);
        if (filename != null) body.put("filename", filename);
        if (caption  != null) body.put("caption",  caption);

        return webClient.post()
                .uri("/send/media")
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(err -> Mono.error(
                                        new FactilizaApiException("WhatsApp API error: " + err))))
                .bodyToMono(JsonPeResponse.class)
                .map(r -> {
                    String wamid = (r != null && r.data() != null) ? r.data().getOrDefault("ID", "").toString() : "";
                    log.info("[WA] ✓ Media enviado | to={} tipo={} wamid={}", to, mediatype, wamid);
                    return wamid;
                })
                .defaultIfEmpty("");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 9) return "51" + digits;
        return digits;
    }

    // ─── Response record ──────────────────────────────────────────────────────

    record JsonPeResponse(Boolean success, String message, Map<String, Object> data) {}

    // ─── Excepción específica ─────────────────────────────────────────────────

    public static class FactilizaApiException extends RuntimeException {
        public FactilizaApiException(String message) {
            super(message);
        }
    }
}
