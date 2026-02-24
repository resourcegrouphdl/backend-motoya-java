package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CuotaCronogramaDocument {

    private Integer cuota;
    /** Alias de cuota — número de cuota (usado en importación de calendario) */
    private Integer cuotaNum;
    /** ISO date YYYY-MM-DD */
    private String fechaVencimiento;
    private Double monto;
    /** EstadoCuota: PAGADA | VENCIDA | VIGENTE | PENDIENTE */
    private String estado;
    /** ISO date YYYY-MM-DD — solo cuando estado == PAGADA */
    private String fechaPago;
}
