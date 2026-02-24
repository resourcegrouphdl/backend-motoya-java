package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.NivelEstrategia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Estrategia {

    private String estrategiaId;
    private String nombre;
    private String descripcion;

    private NivelEstrategia nivelEstrategia;
    private Integer diasMoraMin;
    private Integer diasMoraMax;
    private Boolean activa;

    private String createdAt;
}
