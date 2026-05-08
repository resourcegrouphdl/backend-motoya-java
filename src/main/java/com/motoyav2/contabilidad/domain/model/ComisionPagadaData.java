package com.motoyav2.contabilidad.domain.model;

import java.time.Instant;

/** Comisión de vendedor pagada por Motoya. */
public record ComisionPagadaData(
        String referenciaId,
        String tiendaId,
        double monto,
        Instant fechaPago
) {}
