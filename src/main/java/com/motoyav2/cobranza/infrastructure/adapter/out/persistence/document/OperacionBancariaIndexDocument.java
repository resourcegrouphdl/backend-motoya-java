package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Índice de deduplicación de operaciones bancarias.
 * Document ID: "{BANCO_NORM}_{NUM_OPERACION_NORM}" — clave única que Firestore
 * garantiza con semántica create(), bloqueando cualquier segundo registro.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "cobranzas-operaciones-index")
public class OperacionBancariaIndexDocument {

    @DocumentId
    private String id;

    private String bancoRaw;
    private String numeroOperacionRaw;
    private String voucherId;
    private String contratoId;
    private Double monto;
    private String fechaOperacion;
    private Date   creadoEn;
}