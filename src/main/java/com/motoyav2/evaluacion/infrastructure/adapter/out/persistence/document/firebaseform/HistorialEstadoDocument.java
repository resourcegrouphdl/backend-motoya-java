package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform;

import com.google.cloud.Timestamp;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Documento de auditoría en la colección cambios_estado_solicitud.
 * Contrato TypeScript: HistorialEstado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collectionName = "cambios_estado_solicitud")
public class HistorialEstadoDocument {

    private String id;
    private String solicitudId;
    private String estadoAnterior;
    private String estadoNuevo;
    private Timestamp fechaCambio;
    private String usuarioId;
    private String usuarioNombre;
    private String motivo;
}
