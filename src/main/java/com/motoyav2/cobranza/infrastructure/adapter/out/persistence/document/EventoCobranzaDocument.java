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
 * Event Store del caso — APPEND ONLY.
 * Sub-colección: casos_cobranza/{contratoId}/eventos/{eventoId}
 * Para CollectionGroup queries se usa @Document con el nombre de la sub-colección.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "eventos")
public class EventoCobranzaDocument {

    @DocumentId
    private String id;

    /** Desnormalizado para collectionGroup queries */
    private String contratoId;

    /** TipoEventoCobranza */
    private String tipo;

    /** Objeto tipado según tipo del evento (payload libre) */
    private Map<String, Object> payload;

    private String usuarioId;
    private String usuarioNombre;
    /** true si fue disparado por job/estrategia automática */
    private Boolean automatico;

    /** Inmutable — nunca actualizado */
    private Date creadoEn;
}
