package com.motoyav2.notifications.infrastructure.persistence.adapter;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.motoyav2.notifications.domain.events.BusinessEventType;
import com.motoyav2.notifications.domain.model.*;
import com.motoyav2.notifications.domain.ports.out.NotificationEventRepositoryPort;
import com.motoyav2.notifications.infrastructure.persistence.document.NotificationEventDocument;
import com.motoyav2.notifications.infrastructure.persistence.repository.FirestoreNotificationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventRepositoryAdapter implements NotificationEventRepositoryPort {

    private final FirestoreNotificationEventRepository repository;
    private final Firestore firestore; // SDK nativo para queries compuestos

    @Override
    public Mono<NotificationEvent> save(NotificationEvent event) {
        return repository.save(toDocument(event)).map(this::toDomain);
    }

    @Override
    public Mono<NotificationEvent> update(NotificationEvent event) {
        return repository.save(toDocument(event)).map(this::toDomain);
    }

    @Override
    public Mono<NotificationEvent> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    /**
     * Búsqueda paginada con filtros opcionales. Todos los parámetros son opcionales (null = ignorar).
     * Usa el SDK nativo de Firestore para soportar filtros compuestos dinámicos.
     */
    @Override
    public Flux<NotificationEvent> findByFilters(String eventType, String status,
                                                  String channel, String contratoId, int limit) {
        return Mono.fromCallable(() -> {
                    com.google.cloud.firestore.Query query =
                            firestore.collection("notification_events");

                    if (eventType != null && !eventType.isBlank()) {
                        query = query.whereEqualTo("eventType", eventType);
                    }
                    if (status != null && !status.isBlank()) {
                        query = query.whereEqualTo("status", status);
                    }
                    if (channel != null && !channel.isBlank()) {
                        query = query.whereEqualTo("channel", channel);
                    }
                    if (contratoId != null && !contratoId.isBlank()) {
                        query = query.whereEqualTo("contratoId", contratoId);
                    }

                    return query.orderBy("createdAt",
                                    com.google.cloud.firestore.Query.Direction.DESCENDING)
                            .limit(limit)
                            .get()
                            .get();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(snapshot ->
                        Flux.fromIterable(snapshot.getDocuments())
                                .map(doc -> doc.toObject(NotificationEventDocument.class))
                                .map(this::toDomain)
                );
    }

    /**
     * Query compuesto: status == PENDIENTE AND nextRetryAt <= now
     * Requiere índice compuesto en Firestore (status ASC, nextRetryAt ASC).
     *
     * Usa el SDK de Firestore directamente (no Spring Data) porque Spring Data
     * no soporta operadores <= en Timestamp vía derivación de nombre de método.
     */
    @Override
    public Flux<NotificationEvent> findPendingEventsReadyForRetry(Instant now) {
        Timestamp nowTs = toTimestamp(now);

        return Mono.fromCallable(() ->
                        firestore.collection("notification_events")
                                .whereEqualTo("status", NotificationEventStatus.PENDIENTE.name())
                                .whereLessThanOrEqualTo("nextRetryAt", nowTs)
                                .limit(50) // batch máximo por ciclo
                                .get()
                                .get()
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(snapshot ->
                        Flux.fromIterable(snapshot.getDocuments())
                                .map(doc -> doc.toObject(NotificationEventDocument.class))
                                .map(this::toDomain)
                )
                .doOnNext(e -> log.debug("[OUTBOX] Evento listo para procesar | id={} tipo={}",
                        e.id(), e.eventType()));
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────

    private NotificationEventDocument toDocument(NotificationEvent e) {
        return NotificationEventDocument.builder()
                .id(e.id())
                .eventType(e.eventType() != null ? e.eventType().name() : null)
                .contratoId(e.contratoId())
                .channel(e.channel() != null ? e.channel().name() : null)
                .recipient(e.recipient())
                .template(e.template() != null ? e.template().name() : null)
                .variables(e.variables())
                .status(e.status() != null ? e.status().name() : null)
                .retryCount(e.retryCount())
                .nextRetryAt(e.nextRetryAt() != null ? toTimestamp(e.nextRetryAt()) : null)
                .createdAt(e.createdAt() != null ? toTimestamp(e.createdAt()) : Timestamp.now())
                .processedAt(e.processedAt() != null ? toTimestamp(e.processedAt()) : null)
                .build();
    }

    private NotificationEvent toDomain(NotificationEventDocument doc) {
        return new NotificationEvent(
                doc.getId(),
                doc.getEventType() != null ? BusinessEventType.valueOf(doc.getEventType()) : null,
                doc.getContratoId(),
                doc.getChannel() != null ? NotificationChannel.valueOf(doc.getChannel()) : null,
                doc.getRecipient(),
                doc.getTemplate() != null ? NotificationTemplate.valueOf(doc.getTemplate()) : null,
                doc.getVariables(),
                doc.getStatus() != null ? NotificationEventStatus.valueOf(doc.getStatus()) : null,
                doc.getRetryCount(),
                doc.getNextRetryAt() != null ? doc.getNextRetryAt().toDate().toInstant() : null,
                doc.getCreatedAt() != null ? doc.getCreatedAt().toDate().toInstant() : null,
                doc.getProcessedAt() != null ? doc.getProcessedAt().toDate().toInstant() : null
        );
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }
}
