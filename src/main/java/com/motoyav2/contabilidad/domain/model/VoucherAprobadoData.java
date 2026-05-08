package com.motoyav2.contabilidad.domain.model;

import java.time.Instant;

/** Pago de cliente aprobado en cobranzas. */
public record VoucherAprobadoData(
        String voucherId,
        String contratoId,
        String tiendaId,
        double monto,
        Instant creadoEn
) {}
