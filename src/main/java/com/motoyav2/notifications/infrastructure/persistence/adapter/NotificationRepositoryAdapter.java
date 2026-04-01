package com.motoyav2.notifications.infrastructure.persistence.adapter;

import com.google.cloud.Timestamp;
import com.motoyav2.notifications.domain.model.*;
import com.motoyav2.notifications.domain.ports.out.NotificationRepositoryPort;
import com.motoyav2.notifications.infrastructure.persistence.document.NotificationDocument;
import com.motoyav2.notifications.infrastructure.persistence.repository.FirestoreNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepositoryPort {

    private final FirestoreNotificationRepository repository;

    @Override
    public Mono<Notification> save(Notification notification) {
        return repository.save(toDocument(notification)).map(this::toDomain);
    }

    @Override
    public Mono<Notification> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<Notification> findByStatus(NotificationStatus status) {
        return repository.findByStatus(status.name()).map(this::toDomain);
    }

    @Override
    public Mono<Notification> updateStatus(String id, NotificationStatus status,
                                           String externalMessageId, String lastError) {
        return repository.findById(id)
                .flatMap(doc -> {
                    doc.setStatus(status.name());
                    doc.setLastError(lastError);
                    if (externalMessageId != null && !externalMessageId.isBlank()) {
                        doc.setExternalMessageId(externalMessageId);
                    }
                    if (status == NotificationStatus.ENVIADO) {
                        doc.setSentAt(Timestamp.now());
                    }
                    if (status == NotificationStatus.FALLIDO) {
                        doc.setRetryCount(doc.getRetryCount() + 1);
                    }
                    return repository.save(doc);
                })
                .map(this::toDomain);
    }

    @Override
    public Flux<Notification> findByEventId(String eventId) {
        return repository.findByEventId(eventId).map(this::toDomain);
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────

    private NotificationDocument toDocument(Notification n) {
        return NotificationDocument.builder()
                .id(n.id())
                .channel(n.channel() != null ? n.channel().name() : null)
                .recipient(n.recipient())
                .template(n.template() != null ? n.template().name() : null)
                .variables(n.variables())
                .renderedContent(n.renderedContent())
                .status(n.status() != null ? n.status().name() : null)
                .retryCount(n.retryCount())
                .lastError(n.lastError())
                .createdAt(toTimestamp(n.createdAt() != null ? n.createdAt() : java.time.Instant.now()))
                .sentAt(n.sentAt() != null ? toTimestamp(n.sentAt()) : null)
                .eventId(n.eventId())
                .externalMessageId(n.externalMessageId())
                .providerResponse(n.providerResponse())
                .build();
    }

    private Notification toDomain(NotificationDocument doc) {
        return new Notification(
                doc.getId(),
                doc.getChannel() != null ? NotificationChannel.valueOf(doc.getChannel()) : null,
                doc.getRecipient(),
                doc.getTemplate() != null ? NotificationTemplate.valueOf(doc.getTemplate()) : null,
                doc.getVariables(),
                doc.getRenderedContent(),
                doc.getStatus() != null ? NotificationStatus.valueOf(doc.getStatus()) : null,
                doc.getRetryCount(),
                doc.getLastError(),
                doc.getCreatedAt() != null ? doc.getCreatedAt().toDate().toInstant() : null,
                doc.getSentAt() != null ? doc.getSentAt().toDate().toInstant() : null,
                doc.getEventId(),
                doc.getExternalMessageId(),
                doc.getProviderResponse()
        );
    }

    private Timestamp toTimestamp(java.time.Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }
}
