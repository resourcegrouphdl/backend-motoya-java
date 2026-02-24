package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.OcrResultadoDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Comprobantes de pago subidos por el cliente.
 * La imagenUrl (Signed URL) NO se almacena — se genera on-demand con 15 min de expiración.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "vouchers")
public class VoucherDocument {

    @DocumentId
    private String id;

    /** null si OCR no identificó el contrato */
    private String contratoId;
    private String cliente;
    private String clienteDni;
    private String storeId;

    /** EstadoVoucher: PENDIENTE | APROBADO | RECHAZADO */
    private String estado;

    /** Ruta GCS: vouchers/{voucherId}/original.jpg */
    private String imagenPath;
    /** Ruta GCS: vouchers/{voucherId}/thumb.jpg */
    private String thumbPath;

    /** null si OCR falló */
    private Double montoDetectado;
    /** Cuota vigente del contrato — calculado al vincular */
    private Double montoEsperado;

    private OcrResultadoDocument ocrResultado;

    /** Solo cuando estado == APROBADO */
    private String aprobadoPor;
    private String aprobadoPorNombre;

    /** Solo cuando estado == RECHAZADO */
    private String rechazadoPor;
    /** MotivoRechazoVoucher */
    private String motivoRechazo;
    private String observacionesRechazo;

    /** ID del comprobante generado tras aprobación */
    private String comprobanteId;

    private Date creadoEn;
    private Date procesadoEn;
}
