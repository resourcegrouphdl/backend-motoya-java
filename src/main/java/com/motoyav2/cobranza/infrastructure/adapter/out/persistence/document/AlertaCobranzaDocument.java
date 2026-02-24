package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Alertas operativas para agentes.
 * TTL: campo expiraEn — Firestore TTL Policy elimina automáticamente.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "alertas_cobranza")
public class AlertaCobranzaDocument {

    @DocumentId
    private String id;

    /** TipoAlerta */
    private String tipo;
    /** NivelAlerta: INFO | WARNING | CRITICAL */
    private String nivel;

    private String titulo;
    private String descripcion;

    /** Caso relacionado (si aplica) */
    private String contratoId;
    private String clienteNombre;
    private String storeId;
    /** Si la alerta es para un agente específico */
    private String agenteId;

    private String accionSugerida;
    /** Ruta Angular. Ej: "/cobranzas/vista360/CTR-1001" */
    private String accionRuta;

    private Boolean leida;
    private Boolean descartada;

    private Date creadoEn;
    /** TTL: Firestore elimina el documento automáticamente cuando expiraEn < now() */
    private Date expiraEn;
}
