package com.motoyav2.cobranza.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CuotaAcuerdo {

    private Integer numero;
    /** ISO date YYYY-MM-DD */
    private String fecha;
    private Double monto;
    /** EstadoCuota: PENDIENTE | PAGADA | VENCIDA */
    private String estado;
}
