package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Ledger de deuda — APPEND ONLY. El saldo actual = suma de todos los movimientos.
 * Sub-colección: casos_cobranza/{contratoId}/movimientos/{movId}
 * Positivo = cargo (suma deuda). Negativo = abono (reduce deuda).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "movimientos")
public class MovimientoDocument {

    @DocumentId
    private String id;

    /** Desnormalizado para collectionGroup queries */
    private String contratoId;

    /** TipoMovimiento */
    private String tipo;

    /** Positivo = cargo. Negativo = abono */
    private Double monto;
    private Double saldoAnterior;
    private Double saldoNuevo;
    private String descripcion;

    /** Solo si es un pago vinculado a voucher */
    private String voucherId;
    /** Solo si generó comprobante */
    private String comprobanteId;
    /** Número de cuota afectada */
    private Integer cuotaNumero;
    /** Solo si originado por un acuerdo */
    private String acuerdoId;

    private String autorizadoPor;

    /** Inmutable */
    private Date creadoEn;
}
