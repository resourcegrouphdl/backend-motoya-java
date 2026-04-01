package com.motoyav2.notifications.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * Registro de auditoría de cada notificación enviada (o fallida).
 * Persiste en la colección Firestore "notifications".
 *
 * Campos de trazabilidad fintech (backward compatible — pueden ser null en registros anteriores):
 *   eventId           → ID del NotificationEvent en el Outbox (enlaza log ↔ evento)
 *   externalMessageId → ID asignado por el proveedor: wamid (Meta) o messageId (SMTP)
 *   providerResponse  → Respuesta raw del proveedor (JSON string, para debugging/auditoría)
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
        Instant sentAt,
        String eventId,           // nuevo — nullable para compatibilidad
        String externalMessageId, // nuevo — nullable para compatibilidad
        String providerResponse   // nuevo — nullable para compatibilidad
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
                null,
                request.eventId(),  // puede ser null si se invoca directo (no desde outbox)
                null,
                null
        );
    }

    public Notification withRenderedContent(String content) {
        return new Notification(id, channel, recipient, template, variables,
                content, status, retryCount, lastError, createdAt, sentAt,
                eventId, externalMessageId, providerResponse);
    }

    public Notification withStatus(NotificationStatus newStatus) {
        Instant sent = newStatus == NotificationStatus.ENVIADO ? Instant.now() : sentAt;
        return new Notification(id, channel, recipient, template, variables,
                renderedContent, newStatus, retryCount, lastError, createdAt, sent,
                eventId, externalMessageId, providerResponse);
    }

    public Notification withExternalId(String wamid) {
        return new Notification(id, channel, recipient, template, variables,
                renderedContent, status, retryCount, lastError, createdAt, sentAt,
                eventId, wamid, providerResponse);
    }

    public Notification withError(String error) {
        return new Notification(id, channel, recipient, template, variables,
                renderedContent, NotificationStatus.FALLIDO,
                retryCount + 1, error, createdAt, sentAt,
                eventId, externalMessageId, providerResponse);
    }
}
