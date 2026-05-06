package com.motoyav2.cobranza.application.dto;

import java.util.List;

public record ContextoDuplicadosDto(
        boolean              duplicadoExacto,
        String               voucherDuplicadoId,
        String               banco,
        String               numeroOperacion,
        List<VoucherResumenDto> voucheresSimilares
) {}