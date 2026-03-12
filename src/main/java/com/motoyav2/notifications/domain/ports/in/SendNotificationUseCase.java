package com.motoyav2.notifications.domain.ports.in;

import com.motoyav2.notifications.domain.model.NotificationRequest;
import reactor.core.publisher.Mono;

/**
 * Puerto de entrada: envío directo e inmediato de una notificación.
 * Usado internamente por el procesador del Outbox.
 */
public interface SendNotificationUseCase {
    Mono<Void> send(NotificationRequest request);
}
