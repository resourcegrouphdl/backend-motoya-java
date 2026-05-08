package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/** Colección: contabilidad_movimientos / doc-id = referenciaId */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoContableDocument {
    private String id;
    private String tipo;
    private String contratoId;
    private String tiendaId;
    private String referenciaId;
    private String periodo;
    private double montoTotal;
    private double montoCapital;
    private double montoInteres;
    private double montoCosto;
    private Date creadoEn;
}
