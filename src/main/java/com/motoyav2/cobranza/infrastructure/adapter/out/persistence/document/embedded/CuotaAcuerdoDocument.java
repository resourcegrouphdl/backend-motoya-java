package com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CuotaAcuerdoDocument {

    private Integer numero;
    /** ISO date YYYY-MM-DD */
    private String fecha;
    private Double monto;
    /** EstadoCuota: PENDIENTE | PAGADA | VENCIDA */
    private String estado;
}
