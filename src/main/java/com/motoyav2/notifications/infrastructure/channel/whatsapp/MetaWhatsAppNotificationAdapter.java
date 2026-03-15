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
import java.util.List;
import java.util.Map;

/**
 * Adaptador WhatsApp usando la Meta WhatsApp Business Cloud API.
 *
 * Endpoint base: https://graph.facebook.com/{apiVersion}/{phoneNumberId}/messages
 *
 * Configuración requerida en application.properties:
 *   notifications.meta.phone-number-id  → ID del número Meta Business
 *   notifications.meta.access-token     → Bearer token permanente
 *   notifications.meta.api-version      → ej. v21.0
 *
 * Soporta:
 *   - Mensajes de texto plano (send / sendText)
 *   - Mensajes con archivo adjunto (sendMedia): image, document, video, audio
 */
@Slf4j
@Component
public class MetaWhatsAppNotificationAdapter implements NotificationSenderPort {

    private final WebClient webClient;
    private final MetaWhatsAppProperties properties;

    public MetaWhatsAppNotificationAdapter(
            @Qualifier("metaWhatsAppWebClient") WebClient webClient,
            MetaWhatsAppProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    // ─── NotificationSenderPort ───────────────────────────────────────────────

    @Override
    public Mono<Void> send(Notification notification) {
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
     * @param body      Texto del mensaje
     */
    public Mono<Void> sendText(String recipient, String body) {
        String to = normalizePhone(recipient);

        Map<String, Object> request = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "text",
                "text", Map.of("preview_url", false, "body", body)
        );

        return post(to, request);
    }

    /**
     * Envía un archivo adjunto por WhatsApp.
     *
     * @param recipient Número destino
     * @param mediaUrl  URL pública del archivo (Firebase Storage u otro host accesible)
     * @param mediaType Tipo Meta: "image" | "document" | "video" | "audio"
     * @param filename  Nombre del archivo con extensión (obligatorio para "document")
     * @param caption   Texto que acompaña al archivo (puede ser null)
     */
    public Mono<Void> sendMedia(String recipient, String mediaUrl,
                                String mediaType, String filename, String caption) {
        String to = normalizePhone(recipient);

        Map<String, Object> mediaPayload = new HashMap<>();
        mediaPayload.put("link", mediaUrl);
        if (filename != null) mediaPayload.put("filename", filename);
        if (caption  != null) mediaPayload.put("caption",  caption);

        Map<String, Object> request = new HashMap<>();
        request.put("messaging_product", "whatsapp");
        request.put("to", to);
        request.put("type", mediaType);
        request.put(mediaType, mediaPayload);

        return post(to, request);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Mono<Void> post(String to, Object body) {
        return webClient.post()
                .uri("/{version}/{phoneNumberId}/messages",
                        properties.getApiVersion(), properties.getPhoneNumberId())
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(err -> Mono.error(
                                        new MetaApiException("Meta API error: " + err))))
                .bodyToMono(MetaMessageResponse.class)
                .doOnSuccess(r -> {
                    String messageId = (r != null && r.messages() != null && !r.messages().isEmpty())
                            ? r.messages().get(0).id() : "?";
                    log.info("[META] ✓ Mensaje enviado | to={} messageId={}", to, messageId);
                })
                .then();
    }

    /**
     * Normaliza el número al formato Meta E.164 sin '+'.
     * Número peruano de 9 dígitos → agrega prefijo "51".
     */
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 9) return "51" + digits;
        return digits;
    }

    // ─── Response records ─────────────────────────────────────────────────────

    record MetaMessageResponse(
            String messaging_product,
            List<MetaContact> contacts,
            List<MetaMessage> messages) {}

    record MetaContact(String input, String wa_id) {}

    record MetaMessage(String id) {}

    // ─── Excepción específica ─────────────────────────────────────────────────

    public static class MetaApiException extends RuntimeException {
        public MetaApiException(String message) {
            super(message);
        }
    }
}
