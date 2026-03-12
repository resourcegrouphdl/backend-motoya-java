package com.motoyav2.notifications.infrastructure.adapter.in.web.dto;

import com.motoyav2.notifications.domain.model.NotificationChannel;
import com.motoyav2.notifications.domain.model.NotificationTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request para enviar una notificación desde otro microservicio o el frontend.
 *
 * Ejemplo body:
 * {
 *   "channel": "WHATSAPP",
 *   "recipient": "51987654321",
 *   "template": "RECORDATORIO_CUOTA",
 *   "variables": {
 *     "cliente": "Juan Pérez",
 *     "monto": "S/ 120.00",
 *     "fecha": "15/03/2025"
 *   },
 *   "contratoId": "abc123"   ← opcional, para trazabilidad en el Outbox
 * }
 */
public record SendNotificationRequest(

        @NotNull(message = "El canal es requerido (EMAIL, WHATSAPP, SMS)")
        NotificationChannel channel,

        @NotBlank(message = "El destinatario es requerido")
        String recipient,

        @NotNull(message = "La plantilla es requerida")
        NotificationTemplate template,

        Map<String, String> variables,

        String contratoId
) {}
