package com.motoyav2.calculadora.infrastructure.adapter.in.web.dto;

import com.motoyav2.calculadora.domain.model.ConfiguracionCrediticia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ActualizarConfiguracionRequest(

        @NotNull @DecimalMin("0.00") BigDecimal gastosAdministrativos,
        @NotNull @DecimalMin("0.01") BigDecimal porcentajeInicialMinima,
        @NotNull @DecimalMin("100.00") BigDecimal montoMaximoFinanciar,
        @NotNull @DecimalMin("100.00") BigDecimal montoMinimoFinanciar,
        @NotNull @DecimalMin("0.00") BigDecimal tasaSeguroDesgravamenMensual,
        @Valid ComisionConfigDto comisionDefault,
        @NotNull @DecimalMin("0.01") BigDecimal teaDefault,

        @NotEmpty @Valid List<PlazoTeaConfigDto> plazos
) {
    public ConfiguracionCrediticia toDomain() {
        return ConfiguracionCrediticia.builder()
                .gastosAdministrativos(gastosAdministrativos)
                .porcentajeInicialMinima(porcentajeInicialMinima)
                .montoMaximoFinanciar(montoMaximoFinanciar)
                .montoMinimoFinanciar(montoMinimoFinanciar)
                .tasaSeguroDesgravamenMensual(tasaSeguroDesgravamenMensual)
                .comisionDefault(comisionDefault != null ? comisionDefault.toDomain() : null)
                .teaDefault(teaDefault)
                .plazos(plazos.stream().map(PlazoTeaConfigDto::toDomain).toList())
                .build();
    }
}