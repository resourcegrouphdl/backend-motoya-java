package com.motoyav2.notifications.domain.model;

import lombok.Builder;

import java.util.Map;

/**
 * Modelo de entrada para solicitar el envío de una notificación.
 *
 * Ejemplo:
 * <pre>
 *   NotificationRequest.builder()
 *       .eventId("outbox-event-id")          // opcional: enlaza log con evento outbox
 *       .channel(NotificationChannel.WHATSAPP)
 *       .recipient("51987654321")
 *       .template(NotificationTemplate.RECORDATORIO_CUOTA)
 *       .variables(Map.of("cliente","Juan","monto","S/ 120","fecha","10/03"))
 *       .build();
 * </pre>
 */
@Builder
public record NotificationRequest(
        String eventId,                  // ID del NotificationEvent en el Outbox (trazabilidad)
        NotificationChannel channel,
        String recipient,
        NotificationTemplate template,
        Map<String, String> variables
) {}
