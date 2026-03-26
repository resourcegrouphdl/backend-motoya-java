package com.motoyav2.calculadora.infrastructure.adapter.in.web.dto;

import com.motoyav2.calculadora.domain.model.ConfiguracionCrediticia;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ConfiguracionCrediticiaDto(
        BigDecimal gastosAdministrativos,
        BigDecimal porcentajeInicialMinima,
        BigDecimal montoMaximoFinanciar,
        BigDecimal montoMinimoFinanciar,
        BigDecimal tasaSeguroDesgravamenMensual,
        ComisionConfigDto comisionDefault,
        BigDecimal teaDefault,
        List<PlazoTeaConfigDto> plazos,
        Instant actualizadoEn,
        String  actualizadoPor
) {
    public static ConfiguracionCrediticiaDto from(ConfiguracionCrediticia c) {
        return new ConfiguracionCrediticiaDto(
                c.getGastosAdministrativos(),
                c.getPorcentajeInicialMinima(),
                c.getMontoMaximoFinanciar(),
                c.getMontoMinimoFinanciar(),
                c.getTasaSeguroDesgravamenMensual(),
                ComisionConfigDto.from(c.getComisionDefault()),
                c.getTeaDefault(),
                c.getPlazos().stream().map(PlazoTeaConfigDto::from).toList(),
                c.getActualizadoEn(),
                c.getActualizadoPor()
        );
    }
}