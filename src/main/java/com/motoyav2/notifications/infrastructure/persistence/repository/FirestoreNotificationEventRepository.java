package com.motoyav2.notifications.infrastructure.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.notifications.infrastructure.persistence.document.NotificationEventDocument;
import reactor.core.publisher.Flux;

public interface FirestoreNotificationEventRepository
        extends FirestoreReactiveRepository<NotificationEventDocument> {

    Flux<NotificationEventDocument> findByStatus(String status);
    Flux<NotificationEventDocument> findByContratoId(String contratoId);
}
