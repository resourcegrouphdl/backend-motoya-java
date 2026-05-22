package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Estado temporal de conversación con un número desconocido.
 * ID del documento = telefono (9 dígitos) para lookup O(1).
 * Se elimina cuando el remitente responde o expira (30 min).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "wa_estado_conversacion")
public class EstadoConversacionDocument {

    /** Teléfono normalizado (9 dígitos) — usado como document ID. */
    @DocumentId
    private String telefono;

    /** ESPERANDO_DATOS */
    private String estado;

    /** FK al VoucherSueltoDocument creado cuando llegó la imagen. */
    private String voucherSueltoId;

    private Date creadoEn;

    /** creadoEn + 30 minutos. */
    private Date expiraEn;
}
