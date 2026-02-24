package com.motoyav2.cobranza.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricasAgente {

    private String agenteId;
    private Integer casosAsignados;
    private Integer promesasHoy;
    private Double recuperacionMes;
}
