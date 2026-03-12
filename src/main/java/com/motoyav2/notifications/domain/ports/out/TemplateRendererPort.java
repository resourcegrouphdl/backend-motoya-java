package com.motoyav2.notifications.domain.ports.out;

import com.motoyav2.notifications.domain.model.Notification;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida: renderizado de plantillas dinámicas.
 * La implementación concreta usa Thymeleaf (TEXT para WhatsApp/SMS, HTML para Email).
 */
public interface TemplateRendererPort {
    Mono<String> render(Notification notification);
}
