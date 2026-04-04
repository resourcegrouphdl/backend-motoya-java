package com.motoyav2.notifications.infrastructure.persistence.document;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Documento Firestore: colección "notifications".
 * Registro de auditoría de cada notificación (enviada, fallida o pendiente).
 *
 * Índices recomendados en Firestore:
 *   - status (ASC) → para monitoreo y dashboards
 *   - channel + status (ASC) → para reportes por canal
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collectionName = "notifications")
public class NotificationDocument {

    @DocumentId
    private String id;

    private String channel;
    private String recipient;
    private String template;
    private Map<String, String> variables;
    private String renderedContent;
    private String status;
    private int retryCount;
    private String lastError;
    private Timestamp createdAt;
    private Timestamp sentAt;

    // ─── Campos de trazabilidad fintech (backward compatible) ───────────────
    // Null en documentos anteriores a la versión que los introdujo — ignorar al leer.

    /** ID del NotificationEvent en el Outbox. Enlaza este log con su evento origen. */
    private String eventId;

    /** ID asignado por el proveedor: wamid (Meta WhatsApp) o messageId (SMTP). */
    private String externalMessageId;

    /** Respuesta raw del proveedor en formato JSON string. Para debugging y auditoría. */
    private String providerResponse;
}
