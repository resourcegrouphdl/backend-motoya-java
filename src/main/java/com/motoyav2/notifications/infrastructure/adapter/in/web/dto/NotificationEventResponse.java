package com.motoyav2.notifications.infrastructure.adapter.in.web.dto;

import com.motoyav2.notifications.domain.model.NotificationEvent;

import java.time.Instant;
import java.util.Map;

/**
 * DTO de respuesta para un evento del Outbox (notification_events).
 * Expuesto en GET /api/v1/notifications/events
 */
public record NotificationEventResponse(
        String id,
        String eventType,
        String contratoId,
        String channel,
        String recipient,
        String template,
        String status,
        int retryCount,
        Instant nextRetryAt,
        Instant createdAt,
        Instant processedAt,
        Map<String, String> variables
) {
    public static NotificationEventResponse from(NotificationEvent e) {
        return new NotificationEventResponse(
                e.id(),
                e.eventType() != null ? e.eventType().name() : null,
                e.contratoId(),
                e.channel() != null ? e.channel().name() : null,
                e.recipient(),
                e.template() != null ? e.template().name() : null,
                e.status() != null ? e.status().name() : null,
                e.retryCount(),
                e.nextRetryAt(),
                e.createdAt(),
                e.processedAt(),
                e.variables()
        );
    }
}
