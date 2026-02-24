package com.motoyav2.cobranza.domain.model;

import com.motoyav2.cobranza.domain.enums.CanalContacto;
import com.motoyav2.cobranza.domain.enums.EstadoPromesa;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Promesa {

    private String promesaId;
    private String contratoId;
    private String agenteId;

    private String fechaCompromiso;
    private Double monto;
    private EstadoPromesa estado;
    private CanalContacto canalContacto;

    private String createdAt;
    private String updatedAt;
}
