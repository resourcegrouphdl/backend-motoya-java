package com.motoyav2.calculadora.infrastructure.adapter.in.web.dto;

import com.motoyav2.calculadora.domain.model.PlazoTeaConfig;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlazoTeaConfigDto(
        @NotNull @Min(1) Integer meses,
        @NotNull @DecimalMin("0.01") BigDecimal tea,
        String etiqueta
) {
    public PlazoTeaConfig toDomain() {
        return PlazoTeaConfig.builder()
                .meses(meses)
                .tea(tea)
                .etiqueta(etiqueta != null ? etiqueta : "")
                .build();
    }

    public static PlazoTeaConfigDto from(PlazoTeaConfig p) {
        return new PlazoTeaConfigDto(p.getMeses(), p.getTea(), p.getEtiqueta());
    }
}
