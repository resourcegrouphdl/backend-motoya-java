package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScoresDto {

    private Integer documental;
    private Integer referencias;
    private Integer crediticio;
    private Integer ingresos;
    private Integer entrevistaTitular;
    private Integer entrevistaFiador;

    @JsonProperty("final")
    private Integer scoreFinal;
}
