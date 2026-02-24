package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

/**
 * Dead Letter Queue para Cloud Functions que fallan y no deben perderse.
 * Retención: hasta resolución + 30 días (eliminación manual por ADMIN).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "fallos_procesamiento")
public class FalloProcesamientoDocument {

    @DocumentId
    private String id;

    /** Colección del documento que disparó la función */
    private String coleccionOrigen;
    /** ID del documento que falló */
    private String documentoId;
    /** Nombre de la Cloud Function que falló */
    private String functionName;

    private String error;
    private String stackTrace;
    /** Snapshot del documento al momento del fallo */
    private Map<String, Object> payloadSnapshot;

    private Integer intentos;
    private Boolean resuelta;

    private Date creadoEn;
    private Date resueltaEn;
}
