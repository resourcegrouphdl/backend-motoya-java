package com.motoyav2.notifications.domain.ports.in;

import com.motoyav2.notifications.domain.events.BusinessEventType;
import com.motoyav2.notifications.domain.model.NotificationChannel;
import com.motoyav2.notifications.domain.model.NotificationTemplate;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Puerto de entrada: publica un evento de negocio en el Outbox.
 * Los demás módulos (contrato, finanzas) lo usan para disparar notificaciones
 * sin acoplarse directamente al canal de envío.
 *
 * Migración futura a Kafka: reemplazar la implementación que escribe en Firestore
 * por una que publique en un tópico, sin cambiar esta interfaz.
 */
public interface PublishBusinessEventUseCase {

    Mono<Void> publish(
            BusinessEventType eventType,
            String contratoId,
            NotificationChannel channel,
            String recipient,
            NotificationTemplate template,
            Map<String, String> variables
    );
}
