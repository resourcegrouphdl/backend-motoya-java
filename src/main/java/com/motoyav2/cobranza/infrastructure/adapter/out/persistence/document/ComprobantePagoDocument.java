package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.EmisorDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.ItemComprobanteDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.ReceptorComprobanteDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Facturas/Boletas electrónicas SUNAT.
 * Retención: PERMANENTE (requerimiento SUNAT: mínimo 5 años).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "comprobantes_pago")
public class ComprobantePagoDocument {

    @DocumentId
    private String id;

    /** "B001" (boleta) · "F001" (factura) */
    private String serie;
    /** Correlativo con ceros. Ej: "00000023" */
    private String numero;
    /** serie + "-" + numero. Ej: "B001-00000023" */
    private String numeroCompleto;

    /** TipoComprobante: BOLETA | FACTURA */
    private String tipo;
    /** EstadoComprobante: EMITIDO | ANULADO | PENDIENTE | ERROR_SUNAT */
    private String estado;

    private String contratoId;
    private String voucherId;
    private String storeId;

    private EmisorDocument emisor;
    private ReceptorComprobanteDocument receptor;
    private List<ItemComprobanteDocument> items;

    private Double subTotal;
    /** 18% del subTotal */
    private Double igv;
    /** subTotal + igv */
    private Double total;

    // SUNAT
    private String hashSunat;
    private String cdrSunat;
    private String qrData;

    /** GCS: comprobantes/{id}/factura.pdf */
    private String pdfPath;
    /** GCS: comprobantes/{id}/factura.xml */
    private String xmlPath;

    /** ISO date YYYY-MM-DD */
    private String fechaEmision;

    private Date creadoEn;
    private Date anuladoEn;
    private String motivoAnulacion;

    /** Contador de reintentos de envío SUNAT */
    private Integer intentosSunat;
    private String ultimoErrorSunat;
}
