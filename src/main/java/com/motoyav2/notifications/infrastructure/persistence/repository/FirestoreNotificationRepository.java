package com.motoyav2.notifications.infrastructure.persistence.repository;

import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import com.motoyav2.notifications.infrastructure.persistence.document.NotificationDocument;
import reactor.core.publisher.Flux;

public interface FirestoreNotificationRepository
        extends FirestoreReactiveRepository<NotificationDocument> {

    Flux<NotificationDocument> findByStatus(String status);
}
