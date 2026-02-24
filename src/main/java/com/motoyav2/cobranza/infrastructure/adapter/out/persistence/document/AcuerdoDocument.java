package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaAcuerdoDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Acuerdos de refinanciamiento — mutable.
 * Sub-colección: casos_cobranza/{contratoId}/acuerdos/{acuerdoId}
 * Requiere autorización de supervisor.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "acuerdos")
public class AcuerdoDocument {

    @DocumentId
    private String id;

    /** Desnormalizado para collectionGroup queries */
    private String contratoId;

    /** TipoAcuerdo: REFINANCIAMIENTO | PLAN_PAGOS | QUITA */
    private String tipo;
    /** EstadoAcuerdo: VIGENTE | CUMPLIDO | INCUMPLIDO | CANCELADO */
    private String estado;

    private Integer numeroCuotas;
    private Double montoCuota;
    private Double montoTotalAcordado;
    /** ISO date YYYY-MM-DD — inicio del acuerdo */
    private String fechaInicio;

    private List<CuotaAcuerdoDocument> cuotasPrograma;

    private String autorizadoPor;
    private String autorizadoPorNombre;

    private Date creadoEn;
    private Date actualizadoEn;
}
