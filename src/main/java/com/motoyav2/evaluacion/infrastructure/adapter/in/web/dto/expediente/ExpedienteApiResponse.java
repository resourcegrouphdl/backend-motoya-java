package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.expediente;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExpedienteApiResponse {

    @Builder.Default
    private boolean success = true;

    private ExpedienteDto expediente;
}
