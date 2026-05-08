package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/** Colección: contabilidad_cuotas / doc-id = contratoId */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContabilidadCuotaDocument {
    private String contratoId;
    private String tiendaId;
    private int numeroCuotas;
    private double montoFinanciar;
    private double tasaInteres;
    private double interesTotal;
    private double capitalTotal;
    /** Lista de mapas: {numero, fechaVencimiento, montoTotal, montoCapital, montoInteres} */
    private List<Map<String, Object>> cuotas;
    private Date calculadoEn;
}
