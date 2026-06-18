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
@Document(collectionName = "cobranzas-mensajes-whatsapp")
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

    /** OUTBOUND (enviado al cliente) | INBOUND (recibido del cliente) */
    private String direction;

    /** Solo mensajes INBOUND con media (imagen, documento, audio) */
    private String mediaUrl;
    private String mediaType;
    /** true si el media fue procesado como voucher de pago */
    private Boolean esVoucher;
    private String voucherId;
    /** URL en GCS — se rellena después de subir la imagen (accesible desde el frontend) */
    private String gcsMediaUrl;
    /** Timestamp de recepción (mensajes INBOUND) */
    private Date recibidoEn;
    /** Texto del mensaje entrante si es tipo text */
    private String textoRecibido;

    /** true si fue disparado por estrategia automática */
    private Boolean automatico;
    private String estrategiaId;
    /** Solo si automatico == false */
    private String enviadoPor;

    private String storeId;

    /**
     * Mensaje de error cuando el pipeline de procesamiento falla (OCR, descarga, etc.).
     * Null = sin error. Campo opcional — documentos históricos sin este campo son válidos.
     * El frontend lo muestra como "Error al procesar" en lugar de "procesando...".
     */
    private String errorProcesamiento;

    // ── Campos de ventana de servicio Meta (24h) ─────────────────────────────
    // Todos opcionales y backward compatible — documentos históricos sin estos campos son válidos.

    /** Inicio de la ventana de servicio de 24h con el cliente (primer mensaje INBOUND). */
    private Date ventanaServicioInicio;

    /** Expiración de la ventana de servicio (ventanaServicioInicio + 24h). */
    private Date ventanaServicioExpira;

    /** true si el mensaje fue enviado dentro de la ventana de servicio activa. */
    private Boolean dentroVentanaServicio;

    /**
     * Categoría de precio Meta para este mensaje.
     * Valores: UTILITY, MARKETING, AUTHENTICATION, SERVICE.
     * Mensajes dentro de la ventana de servicio son gratuitos (SERVICE).
     */
    private String categoriaPrecio;

    /**
     * true si este mensaje genera cobro en Meta.
     * false para mensajes dentro de la ventana de servicio de 24h.
     */
    private Boolean billable;
}
