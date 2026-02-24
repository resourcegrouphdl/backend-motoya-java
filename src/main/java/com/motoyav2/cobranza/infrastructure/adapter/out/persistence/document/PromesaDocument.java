package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.spring.data.firestore.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Promesas de pago — mutable (cambia de estado).
 * Sub-colección: casos_cobranza/{contratoId}/promesas/{promesaId}
 * Regla: solo UNA promesa en estado VIGENTE por caso a la vez.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collectionName = "promesas")
public class PromesaDocument {

    @DocumentId
    private String id;

    /** Desnormalizado para collectionGroup queries */
    private String contratoId;

    /** ISO date YYYY-MM-DD — fecha en que el cliente prometió pagar */
    private String fecha;
    private Double monto;

    /** EstadoPromesa: VIGENTE | CUMPLIDA | INCUMPLIDA | CANCELADA */
    private String estado;

    private String observaciones;
    private Date fechaRegistro;
    private String registradaPor;
    private String registradaPorNombre;

    /** Cuándo se cambió de VIGENTE a otro estado */
    private Date cerradaEn;
    /** Solo si estado == CUMPLIDA */
    private Double montoPagado;
    /** Solo si INCUMPLIDA o CANCELADA */
    private String motivoCierre;
}
