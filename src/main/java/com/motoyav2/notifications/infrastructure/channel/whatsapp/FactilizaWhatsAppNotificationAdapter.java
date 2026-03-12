package com.motoyav2.notifications.infrastructure.channel.whatsapp;

import com.motoyav2.notifications.domain.model.Notification;
import com.motoyav2.notifications.domain.model.NotificationChannel;
import com.motoyav2.notifications.domain.ports.out.NotificationSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Adaptador WhatsApp usando la API de Factiliza.
 *
 * Activo cuando: notifications.whatsapp.provider=factiliza (default)
 *
 * Endpoints utilizados:
 *   Texto:  POST /message/sendtext/{instance}  → para mensajes de notificación
 *   Media:  POST /message/sendmedia/{instance} → para enviar PDFs/imágenes (uso futuro)
 *
 * Configuración requerida en application.properties (o env vars):
 *   notifications.factiliza.base-url      → URL base de la API
 *   notifications.factiliza.token         → Bearer token
 *   notifications.factiliza.instance      → nombre de instancia WhatsApp
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "notifications.whatsapp.provider",
        havingValue = "factiliza",
        matchIfMissing = true)
public class FactilizaWhatsAppNotificationAdapter implements NotificationSenderPort {

    private final WebClient webClient;
    private final String instance;

    public FactilizaWhatsAppNotificationAdapter(
            @Qualifier("factilizaWebClient") WebClient webClient,
            FactilizaProperties properties) {
        this.webClient = webClient;
        this.instance = properties.getInstance();
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

    // ─── Métodos públicos reutilizables ───────────────────────────────────────

    /**
     * Envía un mensaje de texto plano por WhatsApp.
     *
     * @param number  Número destino con código de país. Ej: "51987654321"
     * @param message Texto del mensaje
     */
    public Mono<Void> sendText(String number, String message) {
        String normalizedNumber = normalizePhone(number);

        SendTextRequest body = new SendTextRequest(normalizedNumber, message);

        return webClient.post()
                .uri("/message/sendtext/{instance}", instance)
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(rawBody -> {
                            log.debug("[FACTILIZA] Raw response | status={} body={}", response.statusCode(), rawBody);
                            if (response.statusCode().isError()) {
                                return Mono.error(new FactilizaException(
                                        "Factiliza error " + response.statusCode() + ": " + rawBody));
                            }
                            // Factiliza a veces no incluye "success" en el body aunque el mensaje llegue.
                            // Se considera éxito si HTTP 2xx O si el body contiene "success":true.
                            boolean exito = response.statusCode().is2xxSuccessful()
                                    && !rawBody.contains("\"success\":false");
                            if (!exito) {
                                return Mono.error(new FactilizaException(
                                        "Factiliza rechazó el mensaje: " + rawBody));
                            }
                            log.info("[FACTILIZA] ✓ Texto enviado | to={}", normalizedNumber);
                            return Mono.<Void>empty();
                        }));
    }

    /**
     * Envía un archivo (imagen, documento, video, audio) por WhatsApp.
     *
     * @param number    Número destino con código de país
     * @param mediaUrl  URL pública del archivo (o Base64 si es local)
     * @param mediaType "image" | "document" | "video" | "audio"
     * @param filename  Nombre del archivo con extensión (ej: "contrato.pdf")
     * @param caption   Texto que acompaña al archivo (opcional, puede ser null)
     */
    public Mono<Void> sendMedia(String number, String mediaUrl,
                                String mediaType, String filename, String caption) {
        String normalizedNumber = normalizePhone(number);

        SendMediaRequest body = new SendMediaRequest(
                normalizedNumber, mediaType, mediaUrl, filename, caption);

        return webClient.post()
                .uri("/message/sendmedia/{instance}", instance)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new FactilizaException(
                                        "Factiliza media error " + response.statusCode() + ": " + errorBody)))
                )
                .bodyToMono(FactilizaResponse.class)
                .flatMap(response -> {
                    if (!Boolean.TRUE.equals(response.success())) {
                        return Mono.error(new FactilizaException(
                                "Factiliza rechazó el archivo: " + response.message()));
                    }
                    log.info("[FACTILIZA] ✓ Media enviado | to={} tipo={} archivo={}",
                            normalizedNumber, mediaType, filename);
                    return Mono.empty();
                });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Normaliza el número de teléfono al formato Factiliza: solo dígitos con código de país.
     * Si es un número peruano de 9 dígitos, agrega "51" automáticamente.
     */
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 9) {
            return "51" + digits;
        }
        return digits;
    }

    // ─── Request / Response records ───────────────────────────────────────────

    record SendTextRequest(String number, String text) {}

    record SendMediaRequest(
            String number,
            String mediatype,
            String media,
            String filename,
            String caption) {}

    record FactilizaResponse(
            Integer status,
            Boolean success,
            String message) {}

    // ─── Excepción específica ─────────────────────────────────────────────────

    public static class FactilizaException extends RuntimeException {
        public FactilizaException(String message) {
            super(message);
        }
    }
}
