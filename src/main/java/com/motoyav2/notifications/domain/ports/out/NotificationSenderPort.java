package com.motoyav2.notifications.domain.ports.out;

import com.motoyav2.notifications.domain.model.Notification;
import com.motoyav2.notifications.domain.model.NotificationChannel;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida: adaptadores de canal (WhatsApp, Email, SMS).
 * Cada implementación declara su canal mediante {@link #channel()},
 * lo que permite selección dinámica sin switches.
 */
public interface NotificationSenderPort {

    Mono<Void> send(Notification notification);

    NotificationChannel channel();
}
