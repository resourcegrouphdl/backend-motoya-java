package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Registro inmutable de cada ejecución de una estrategia — APPEND ONLY.
 * Retención: 1 año (archivado manual a Cloud Storage).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "disparos_estrategia")
public class DisparoEstrategiaDocument {

    @DocumentId
    private String id;

    private String estrategiaId;
    private String contratoId;
    private String storeId;
    /** CanalContacto */
    private String canal;
    /** Snapshot del mensaje real con variables reemplazadas */
    private String mensajeEnviado;

    /** EstadoDisparoEstrategia: ENVIADO | ENTREGADO | FALLIDO */
    private String estado;
    /** ResultadoDisparo: CONTACTO_EXITOSO | SIN_RESPUESTA | PROMESA_GENERADA */
    private String resultado;

    private Date enviadoEn;
    private Date resultadoEn;
}
