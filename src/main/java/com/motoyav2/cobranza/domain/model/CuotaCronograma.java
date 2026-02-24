package com.motoyav2.cobranza.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CuotaCronograma {

    private Integer cuota;
    /** ISO date YYYY-MM-DD */
    private String fechaVencimiento;
    private Double monto;
    /** EstadoCuota: PAGADA | VENCIDA | VIGENTE | PENDIENTE */
    private String estado;
    /** ISO date YYYY-MM-DD — solo cuando estado == PAGADA */
    private String fechaPago;
}
