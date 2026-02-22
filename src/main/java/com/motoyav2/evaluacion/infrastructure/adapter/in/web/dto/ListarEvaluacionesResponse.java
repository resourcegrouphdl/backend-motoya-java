package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListarEvaluacionesResponse {

    @Builder.Default
    private boolean success = true;

    private List<EvaluacionResumenDto> data;
    private long total;
    private int pagina;
    private int porPagina;
}
