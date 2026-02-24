package com.motoyav2.cobranza.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Numerador {

    /** Serie del comprobante, ej: "B001" */
    private String serie;
    private Long ultimoNumero;
    private String updatedAt;
}
