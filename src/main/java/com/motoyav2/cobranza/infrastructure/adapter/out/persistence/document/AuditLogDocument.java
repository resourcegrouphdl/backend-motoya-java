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
 * Registro de auditoría inmutable — TTL 90 días.
 * Sin update ni delete.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "audit_log")
public class AuditLogDocument {

    @DocumentId
    private String id;

    /** Ej: "APROBAR_VOUCHER", "GENERAR_COMPROBANTE", "ELIMINAR_ALERTA" */
    private String accion;
    /** Colección afectada. Ej: "vouchers" */
    private String entidad;
    private String entidadId;

    private String usuarioId;
    private String usuarioNombre;
    private String ip;
    /** Diff antes/después del cambio */
    private Map<String, Object> detalles;

    /** Inmutable — nunca actualizado */
    private Date timestamp;
    /** timestamp + 90 días — usado por TTL policy de Firestore */
    private Date expiraEn;
}
