package com.motoyav2.notifications.infrastructure.adapter.in.web.dto;

/**
 * Respuesta tras solicitar el envío de una notificación.
 *
 * mode:
 *   "DIRECT" → enviado de forma síncrona (puede tardar más, responde cuando el envío termina)
 *   "ASYNC"  → guardado en el Outbox, será procesado por el trigger de Cloud Function
 */
public record SendNotificationResponse(
        String mode,
        String status,
        String message,
        String eventId
) {
    public static SendNotificationResponse direct() {
        return new SendNotificationResponse("DIRECT", "SENT", "Notificación enviada exitosamente", null);
    }

    public static SendNotificationResponse async(String eventId) {
        return new SendNotificationResponse("ASYNC", "QUEUED",
                "Evento registrado en el Outbox. Será procesado en breve.", eventId);
    }
}
