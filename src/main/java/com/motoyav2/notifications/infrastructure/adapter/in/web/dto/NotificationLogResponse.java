package com.motoyav2.notifications.infrastructure.adapter.in.web.dto;

import com.motoyav2.notifications.domain.model.Notification;

import java.time.Instant;

/**
 * DTO de respuesta para un registro de auditoría (notifications).
 * Expuesto en GET /api/v1/notifications/events/{id}/logs
 */
public record NotificationLogResponse(
        String id,
        String eventId,
        String channel,
        String recipient,
        String template,
        String status,
        int retryCount,
        String lastError,
        String externalMessageId,
        Instant createdAt,
        Instant sentAt
) {
    public static NotificationLogResponse from(Notification n) {
        return new NotificationLogResponse(
                n.id(),
                n.eventId(),
                n.channel() != null ? n.channel().name() : null,
                n.recipient(),
                n.template() != null ? n.template().name() : null,
                n.status() != null ? n.status().name() : null,
                n.retryCount(),
                n.lastError(),
                n.externalMessageId(),
                n.createdAt(),
                n.sentAt()
        );
    }
}
