package com.motoyav2.notifications.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * Registro de auditoría de cada notificación enviada (o fallida).
 * Persiste en la colección Firestore "notifications".
 */
public record Notification(
        String id,
        NotificationChannel channel,
        String recipient,
        NotificationTemplate template,
        Map<String, String> variables,
        String renderedContent,
        NotificationStatus status,
        int retryCount,
        String lastError,
        Instant createdAt,
        Instant sentAt
) {

    public static Notification create(NotificationRequest request) {
        return new Notification(
                null,
                request.channel(),
                request.recipient(),
                request.template(),
                request.variables(),
                null,
                NotificationStatus.PENDIENTE,
                0,
                null,
                Instant.now(),
                null
        );
    }

    public Notification withRenderedContent(String content) {
        return new Notification(id, channel, recipient, template, variables,
                content, status, retryCount, lastError, createdAt, sentAt);
    }

    public Notification withStatus(NotificationStatus newStatus) {
        Instant sent = newStatus == NotificationStatus.ENVIADO ? Instant.now() : sentAt;
        return new Notification(id, channel, recipient, template, variables,
                renderedContent, newStatus, retryCount, lastError, createdAt, sent);
    }

    public Notification withError(String error) {
        return new Notification(id, channel, recipient, template, variables,
                renderedContent, NotificationStatus.FALLIDO,
                retryCount + 1, error, createdAt, sentAt);
    }
}
