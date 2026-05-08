package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BucketMora {

    /** AL_DIA | 1_30 | 31_60 | 61_90 | MAS_90 */
    String tramo;
    String label;
    int cantidadContratos;
    Double montoSaldo;
    Double porcentaje;
}
