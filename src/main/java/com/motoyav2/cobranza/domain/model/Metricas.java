package com.motoyav2.cobranza.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Metricas {

    /** Singleton — siempre "resumen_actual" */
    private String metricasId;
    /** Mapa agenteId → métricas del agente */
    private Map<String, MetricasAgente> agentes;
    private String updatedAt;
}
