package com.motoyav2.notifications.domain.ports.out;

import com.motoyav2.notifications.domain.model.NotificationEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Puerto de salida: Outbox de eventos de notificación (colección "notification_events").
 * Permite desacoplar la publicación del evento del envío real y soportar reintentos.
 *
 * Migración a Kafka: esta interfaz permanece igual; solo cambia la implementación
 * en infraestructura (Firestore → Kafka Producer).
 */
public interface NotificationEventRepositoryPort {
    Mono<NotificationEvent> save(NotificationEvent event);
    Mono<NotificationEvent> update(NotificationEvent event);

    /**
     * Busca eventos PENDIENTE cuyo nextRetryAt sea <= now.
     * Usa query compuesto en Firestore: requiere índice compuesto (status, nextRetryAt ASC).
     */
    Flux<NotificationEvent> findPendingEventsReadyForRetry(Instant now);
}
