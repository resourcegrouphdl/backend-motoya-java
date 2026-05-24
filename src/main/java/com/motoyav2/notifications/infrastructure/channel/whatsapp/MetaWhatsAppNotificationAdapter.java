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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador para Meta WhatsApp Cloud API.
 *
 * Endpoints usados:
 *   POST  /{apiVersion}/{phoneNumberId}/messages  — enviar mensaje (texto, template, media)
 *   GET   /{apiVersion}/{mediaId}                 — obtener URL de media entrante
 *
 * Configuración requerida (variables de entorno):
 *   META_WA_PHONE_NUMBER_ID  → ID numérico del número en el WABA
 *   META_WA_ACCESS_TOKEN     → Token de acceso permanente de la app
 */
@Slf4j
@Component
public class MetaWhatsAppNotificationAdapter implements NotificationSenderPort {

    private final WebClient             webClient;
    private final MetaWhatsAppProperties properties;
    private final MetaTemplateRegistry  registry;

    public MetaWhatsAppNotificationAdapter(
            @Qualifier("metaWhatsAppWebClient") WebClient webClient,
            MetaWhatsAppProperties properties,
            MetaTemplateRegistry registry) {
        this.webClient  = webClient;
        this.properties = properties;
        this.registry   = registry;
    }

    // ─── NotificationSenderPort (Outbox) ─────────────────────────────────────

    @Override
    public Mono<String> send(Notification notification) {
        return registry.find(notification.template())
                .map(config -> sendTemplate(notification.recipient(), config, notification.variables()))
                .orElseGet(() -> {
                    log.warn("[META-WA] Template no registrado en Meta para {}; enviando como texto",
                            notification.template());
                    return sendText(notification.recipient(), notification.renderedContent());
                });
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    // ─── Métodos públicos (llamadas directas desde cobranza/evaluación) ───────

    /**
     * Envía texto libre. Solo válido dentro de la ventana de 24h (cliente escribió primero).
     * Fuera de la ventana Meta rechaza el envío — usar sendTemplate en su lugar.
     */
    public Mono<String> sendText(String recipient, String text) {
        String to = normalizePhone(recipient);
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type",    "individual",
                "to",                to,
                "type",              "text",
                "text",              Map.of("preview_url", false, "body", text)
        );
        return postMessages(body)
                .doOnSuccess(id -> log.info("[META-WA] ✓ Texto enviado | to={} wamid={}", to, id));
    }

    /**
     * Envía un documento o imagen.
     *
     * @param mediaType  "image" | "document" | "video" | "audio"
     * @param mediaUrl   URL pública accesible por Meta
     * @param filename   Nombre del archivo (solo para "document")
     * @param caption    Texto que acompaña al media (puede ser null)
     */
    public Mono<String> sendMedia(String recipient, String mediaType,
                                   String mediaUrl, String filename, String caption) {
        String to = normalizePhone(recipient);
        Map<String, Object> mediaPayload = new HashMap<>();
        mediaPayload.put("link", mediaUrl);
        if (filename != null) mediaPayload.put("filename", filename);
        if (caption  != null) mediaPayload.put("caption",  caption);

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type",    "individual");
        body.put("to",                to);
        body.put("type",              mediaType);
        body.put(mediaType,           mediaPayload);

        return postMessages(body)
                .doOnSuccess(id -> log.info("[META-WA] ✓ Media enviado | to={} tipo={} wamid={}", to, mediaType, id));
    }

    /**
     * Descarga los bytes de un media entrante usando el mediaId de Meta.
     * Necesario para procesar imágenes/documentos recibidos por webhook.
     */
    public Mono<byte[]> downloadMedia(String mediaId) {
        return webClient.get()
                .uri("/{version}/{mediaId}", properties.getApiVersion(), mediaId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r ->
                        r.bodyToMono(String.class).flatMap(err ->
                                Mono.error(new MetaApiException("Error obteniendo URL de media: " + err))))
                .bodyToMono(MediaUrlResponse.class)
                .flatMap(resp -> {
                    if (resp == null || resp.url() == null) {
                        return Mono.error(new MetaApiException("Meta no devolvió URL para mediaId=" + mediaId));
                    }
                    return webClient.get()
                            .uri(resp.url())
                            .retrieve()
                            .bodyToMono(byte[].class);
                });
    }

    // ─── Helpers internos ─────────────────────────────────────────────────────

    private Mono<String> sendTemplate(String recipient,
                                       MetaTemplateRegistry.MetaTemplateConfig config,
                                       Map<String, String> variables) {
        String to = normalizePhone(recipient);

        List<Map<String, Object>> parameters = new ArrayList<>();
        for (String slot : config.paramSlots()) {
            parameters.add(Map.of("type", "text", "text",
                    variables.getOrDefault(slot, "")));
        }

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type",    "individual",
                "to",                to,
                "type",              "template",
                "template",          Map.of(
                        "name",       config.metaName(),
                        "language",   Map.of("code", config.languageCode()),
                        "components", List.of(Map.of(
                                "type",       "body",
                                "parameters", parameters
                        ))
                )
        );

        return postMessages(body)
                .doOnSuccess(id -> log.info("[META-WA] ✓ Template enviado | to={} template={} wamid={}",
                        to, config.metaName(), id));
    }

    private Mono<String> postMessages(Map<String, Object> body) {
        return webClient.post()
                .uri("/{version}/{phoneNumberId}/messages",
                        properties.getApiVersion(), properties.getPhoneNumberId())
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(err -> Mono.error(new MetaApiException("Meta API error: " + err))))
                .bodyToMono(MetaMessageResponse.class)
                .map(r -> {
                    if (r != null && r.messages() != null && !r.messages().isEmpty()) {
                        String wamid = r.messages().get(0).getOrDefault("id", "").toString();
                        return wamid;
                    }
                    return "";
                })
                .defaultIfEmpty("");
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 9) return "51" + digits;
        if (digits.startsWith("51") && digits.length() == 11) return digits;
        return "51" + digits;
    }

    // ─── Response records ─────────────────────────────────────────────────────

    record MetaMessageResponse(
            String messagingProduct,
            List<Map<String, Object>> contacts,
            List<Map<String, Object>> messages) {}

    record MediaUrlResponse(String url, String mimeType, String sha256, String fileSize, String id) {}

    // ─── Excepción específica ─────────────────────────────────────────────────

    public static class MetaApiException extends RuntimeException {
        public MetaApiException(String message) { super(message); }
    }
}