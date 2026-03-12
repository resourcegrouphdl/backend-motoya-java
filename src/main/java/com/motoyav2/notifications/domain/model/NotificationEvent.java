package com.motoyav2.notifications.domain.model;

import com.motoyav2.notifications.domain.events.BusinessEventType;

import java.time.Instant;
import java.util.Map;

/**
 * Evento de dominio almacenado en el Outbox (colección Firestore "notification_events").
 * Permite desacoplar la creación del evento del envío real, soportar reintentos
 * y facilitar la migración futura a Kafka.
 */
public record NotificationEvent(
        String id,
        BusinessEventType eventType,
        String contratoId,
        NotificationChannel channel,
        String recipient,
        NotificationTemplate template,
        Map<String, String> variables,
        NotificationEventStatus status,
        int retryCount,
        Instant nextRetryAt,
        Instant createdAt,
        Instant processedAt
) {

    private static final int MAX_RETRIES = 5;
    // Estrategia de backoff: 1min, 5min, 30min, 2h, 8h
    private static final long[] RETRY_DELAYS_SECONDS = {60, 300, 1800, 7200, 28800};

    public static NotificationEvent create(
            BusinessEventType eventType,
            String contratoId,
            NotificationChannel channel,
            String recipient,
            NotificationTemplate template,
            Map<String, String> variables) {
        return new NotificationEvent(
                null, eventType, contratoId, channel, recipient, template, variables,
                NotificationEventStatus.PENDIENTE, 0, Instant.now(), Instant.now(), null
        );
    }

    public NotificationEvent markProcessing() {
        return new NotificationEvent(id, eventType, contratoId, channel, recipient, template,
                variables, NotificationEventStatus.PROCESANDO, retryCount,
                nextRetryAt, createdAt, null);
    }

    public NotificationEvent markCompleted() {
        return new NotificationEvent(id, eventType, contratoId, channel, recipient, template,
                variables, NotificationEventStatus.COMPLETADO, retryCount,
                nextRetryAt, createdAt, Instant.now());
    }

    public NotificationEvent scheduleRetry() {
        int newRetryCount = retryCount + 1;
        if (newRetryCount > MAX_RETRIES) {
            return markFailed();
        }
        long delaySecs = RETRY_DELAYS_SECONDS[Math.min(newRetryCount - 1, RETRY_DELAYS_SECONDS.length - 1)];
        Instant nextRetry = Instant.now().plusSeconds(delaySecs);
        return new NotificationEvent(id, eventType, contratoId, channel, recipient, template,
                variables, NotificationEventStatus.PENDIENTE, newRetryCount,
                nextRetry, createdAt, null);
    }

    public NotificationEvent markFailed() {
        return new NotificationEvent(id, eventType, contratoId, channel, recipient, template,
                variables, NotificationEventStatus.FALLIDO, retryCount,
                nextRetryAt, createdAt, Instant.now());
    }

    public boolean hasExceededMaxRetries() {
        return retryCount >= MAX_RETRIES;
    }
}
