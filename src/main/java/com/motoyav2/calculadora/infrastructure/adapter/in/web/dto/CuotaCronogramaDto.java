package com.motoyav2.calculadora.infrastructure.adapter.in.web.dto;

import com.motoyav2.calculadora.domain.model.CuotaCronograma;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CuotaCronogramaDto(
        int numeroCuota,
        LocalDate fechaVencimiento,
        BigDecimal saldoInicial,
        BigDecimal interes,
        BigDecimal amortizacion,
        BigDecimal seguroDesgravamen,
        BigDecimal cuotaTotal,
        BigDecimal saldoFinal
) {
    public static CuotaCronogramaDto from(CuotaCronograma c) {
        return new CuotaCronogramaDto(
                c.getNumeroCuota(),
                c.getFechaVencimiento(),
                c.getSaldoInicial(),
                c.getInteres(),
                c.getAmortizacion(),
                c.getSeguroDesgravamen(),
                c.getCuotaTotal(),
                c.getSaldoFinal()
        );
    }
}
