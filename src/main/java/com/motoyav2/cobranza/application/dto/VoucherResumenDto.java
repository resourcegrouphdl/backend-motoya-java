package com.motoyav2.cobranza.application.dto;

public record VoucherResumenDto(
        String voucherId,
        String estado,
        Double monto,
        String fechaOperacion,
        String banco,
        String numeroOperacion,
        String aprobadoEn
) {}