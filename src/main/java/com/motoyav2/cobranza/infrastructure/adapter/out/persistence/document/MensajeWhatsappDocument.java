package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Log de mensajes WhatsApp enviados.
 * Retención: 2 años (archivado manual a Cloud Storage).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "mensajes_whatsapp")
public class MensajeWhatsappDocument {

    @DocumentId
    private String id;

    private String contratoId;
    private String clienteNombre;
    /** Formato: +51xxx */
    private String telefono;

    private String plantillaId;
    private String plantillaNombre;
    private String mensajeReal;

    /** EstadoMensajeWa: PENDIENTE | ENVIADO | ENTREGADO | LEIDO | FALLIDO */
    private String estado;
    /** WhatsApp Message ID retornado por Twilio */
    private String wamid;

    private Date enviadoEn;
    /** Actualizado por webhook Twilio */
    private Date entregadoEn;
    /** Actualizado por webhook Twilio */
    private Date leidoEn;

    private String errorDetalle;

    /** true si fue disparado por estrategia automática */
    private Boolean automatico;
    private String estrategiaId;
    /** Solo si automatico == false */
    private String enviadoPor;

    private String storeId;
}
