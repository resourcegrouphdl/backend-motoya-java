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
 * Documento Firestore: colección "notification_events".
 * Implementa el Outbox Pattern: almacena eventos de negocio pendientes de envío.
 *
 * Índice compuesto REQUERIDO en Firestore Console:
 *   Colección: notification_events
 *   Campos: status (ASC), nextRetryAt (ASC)
 *   Modo: Collection
 *
 * Para crear el índice: Firebase Console → Firestore → Índices → Compuesto → Agregar
 * o mediante firebase CLI: firebase deploy --only firestore:indexes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collectionName = "notification_events")
public class NotificationEventDocument {

    @DocumentId
    private String id;

    /** Tipo de evento de negocio (BusinessEventType.name()). */
    private String eventType;

    /** ID del contrato relacionado (trazabilidad). */
    private String contratoId;

    private String channel;
    private String recipient;
    private String template;
    private Map<String, String> variables;

    /** Estado del evento en el Outbox (NotificationEventStatus.name()). */
    private String status;

    /** Número de reintentos realizados (0 = primer intento). */
    private int retryCount;

    /** Momento a partir del cual el scheduler puede procesar este evento. */
    private Timestamp nextRetryAt;

    private Timestamp createdAt;
    private Timestamp processedAt;
}
