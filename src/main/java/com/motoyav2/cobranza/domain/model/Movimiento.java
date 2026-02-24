package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.TipoMovimiento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Movimiento {

    private String movimientoId;
    private String contratoId;
    private TipoMovimiento tipo;
    /** Positivo = cargo, negativo = abono */
    private Double monto;
    private String descripcion;
    private String referencia;
    private String timestamp;
}
