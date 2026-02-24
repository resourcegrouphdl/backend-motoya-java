package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Secuencia correlativa para series de comprobantes.
 * ID del documento = serie. Ej: "B001", "F001"
 * SIEMPRE usar Firestore transaction para leer-incrementar-escribir atómicamente.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "numeradores")
public class NumeradorDocument {

    @DocumentId
    private String serie;

    /** El correlativo más reciente emitido */
    private Long ultimoNumero;
    private Date actualizadoEn;
}
