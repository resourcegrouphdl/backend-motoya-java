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

    /**
     * Actualiza el estado de la notificación incluyendo el ID externo del proveedor.
     *
     * @param id                ID del documento en Firestore
     * @param status            Nuevo estado
     * @param externalMessageId wamid (Meta) o messageId (SMTP). Null si no aplica.
     * @param lastError         Mensaje de error. Null en caso de éxito.
     */
    Mono<Notification> updateStatus(String id, NotificationStatus status,
                                    String externalMessageId, String lastError);

    /**
     * Recupera todos los logs de una notificación a partir del evento Outbox que los originó.
     * Permite construir la timeline de intentos por evento.
     */
    Flux<Notification> findByEventId(String eventId);
}
