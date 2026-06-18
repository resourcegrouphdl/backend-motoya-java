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
import reactor.util.retry.Retry;

import java.time.Duration;

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
        log.warn("[META-WA] ⚠ Texto libre a={} — SOLO funciona si el destinatario escribió en las últimas 24h (ventana Meta). Usa sendTemplateRaw() para mensajes proactivos.", to);
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type",    "individual",
                "to",                to,
                "type",              "text",
                "text",              Map.of("preview_url", false, "body", text)
        );
        return postMessages(body)
                .doOnSuccess(id -> log.info("[META-WA] ✓ Texto enviado | to={} wamid={} (Meta acepta pero puede no entregar si ventana cerrada)", to, id));
    }

    /**
     * Envía un template de Meta directamente por nombre y params posicionales.
     * Usado desde DebugWaController para probar plantillas sin requerir contratoId.
     *
     * @param metaName         Nombre exacto del template en Meta (ej: motoya_recordatorio_cuota)
     * @param languageCode     Código de idioma (ej: es_PE)
     * @param paramsOrdenados  Valores en el orden posicional que Meta espera ({{1}}, {{2}}, ...)
     */
    public Mono<String> sendTemplateRaw(String recipient, String metaName,
                                        String languageCode, List<String> paramsOrdenados) {
        String to = normalizePhone(recipient);
        List<Map<String, Object>> parameters = paramsOrdenados.stream()
                .map(v -> Map.<String, Object>of("type", "text", "text", v))
                .toList();
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type",    "individual",
                "to",                to,
                "type",              "template",
                "template",          Map.of(
                        "name",       metaName,
                        "language",   Map.of("code", languageCode),
                        "components", List.of(Map.of(
                                "type",       "body",
                                "parameters", parameters
                        ))
                )
        );
        return postMessages(body)
                .doOnSuccess(id -> log.info("[META-WA] ✓ Template raw enviado | to={} template={} wamid={}", to, metaName, id));
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
     * IMPORTANTE: Meta expira el mediaId en 10 minutos — esta llamada debe
     * ejecutarse lo antes posible tras recibir el webhook.
     *
     * Incluye retry con backoff exponencial (3 intentos: 1s, 2s, 4s).
     * No reintenta si el mediaId ya expiró (MetaApiException con 404).
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
                    log.debug("[META-WA] Descargando media | mediaId={} mime={}", mediaId, resp.mimeType());
                    return webClient.get()
                            .uri(resp.url())
                            .retrieve()
                            .bodyToMono(byte[].class);
                })
                // Retry con backoff exponencial — solo reintenta errores transitorios (no 404)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(8))
                        .filter(ex -> !(ex instanceof MetaApiException))
                        .doBeforeRetry(sig -> log.warn("[META-WA] Reintentando descarga de media | mediaId={} intento={}",
                                mediaId, sig.totalRetries() + 1)))
                .doOnSuccess(bytes -> log.info("[META-WA] Media descargado | mediaId={} size={}KB",
                        mediaId, bytes != null ? bytes.length / 1024 : 0));
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
                .flatMap(r -> {
                    if (r != null && r.messages() != null && !r.messages().isEmpty()) {
                        String wamid = r.messages().get(0).getOrDefault("id", "").toString();
                        if (!wamid.isBlank()) return Mono.just(wamid);
                    }
                    return Mono.error(new MetaApiException(
                        "Meta no devolvió wamid — revisa: token válido, phoneNumberId correcto, template aprobado y nombre exacto"));
                });
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