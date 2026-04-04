package com.motoyav2.notifications.domain.ports.out;

import com.motoyav2.notifications.domain.model.Notification;
import com.motoyav2.notifications.domain.model.NotificationChannel;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida: adaptadores de canal (WhatsApp, Email, SMS).
 * Cada implementación declara su canal mediante {@link #channel()},
 * lo que permite selección dinámica sin switches.
 *
 * {@code send()} retorna el ID externo del mensaje (wamid de Meta, messageId de email).
 * Si el proveedor no retorna ID, devuelve cadena vacía. Nunca retorna null.
 */
public interface NotificationSenderPort {

    /**
     * Envía la notificación y retorna el ID externo asignado por el proveedor.
     * WhatsApp (Meta): wamid (ej. "wamid.ABCDEF...")
     * Email:           messageId del servidor SMTP (ej. "<abc@mail.motoya.com>")
     * Si el proveedor no retorna ID: ""
     */
    Mono<String> send(Notification notification);

    NotificationChannel channel();
}
