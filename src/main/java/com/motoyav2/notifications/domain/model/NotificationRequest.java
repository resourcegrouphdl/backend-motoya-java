package com.motoyav2.notifications.domain.model;

import lombok.Builder;

import java.util.Map;

/**
 * Modelo de entrada para solicitar el envío de una notificación.
 *
 * Ejemplo:
 * <pre>
 *   NotificationRequest.builder()
 *       .channel(NotificationChannel.WHATSAPP)
 *       .recipient("51987654321")
 *       .template(NotificationTemplate.RECORDATORIO_CUOTA)
 *       .variables(Map.of("cliente","Juan","monto","S/ 120","fecha","10/03"))
 *       .build();
 * </pre>
 */
@Builder
public record NotificationRequest(
        NotificationChannel channel,
        String recipient,
        NotificationTemplate template,
        Map<String, String> variables
) {}
