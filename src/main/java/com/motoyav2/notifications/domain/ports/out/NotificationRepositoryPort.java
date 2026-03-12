package com.motoyav2.notifications.domain.ports.out;

import com.motoyav2.notifications.domain.model.Notification;
import com.motoyav2.notifications.domain.model.NotificationStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida: persistencia del log de notificaciones (colección "notifications").
 * Permite auditoría, trazabilidad y monitoreo de cada intento de envío.
 */
public interface NotificationRepositoryPort {
    Mono<Notification> save(Notification notification);
    Mono<Notification> findById(String id);
    Flux<Notification> findByStatus(NotificationStatus status);
    Mono<Notification> updateStatus(String id, NotificationStatus status, String lastError);
}
