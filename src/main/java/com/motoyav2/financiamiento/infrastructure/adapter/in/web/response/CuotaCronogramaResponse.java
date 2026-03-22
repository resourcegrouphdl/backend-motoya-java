package com.motoyav2.financiamiento.infrastructure.adapter.in.web.response;

import com.motoyav2.financiamiento.domain.model.CuotaCronograma;

import java.math.BigDecimal;

public record CuotaCronogramaResponse(
        int numero,
        BigDecimal saldoInicial,
        BigDecimal interes,
        BigDecimal amortizacion,
        BigDecimal cuota,
        BigDecimal saldoFinal
) {
    public static CuotaCronogramaResponse from(CuotaCronograma c) {
        return new CuotaCronogramaResponse(
                c.getNumero(),
                c.getSaldoInicial(),
                c.getInteres(),
                c.getAmortizacion(),
                c.getCuota(),
                c.getSaldoFinal()
        );
    }
}
