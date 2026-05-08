package com.motoyav2.contabilidad.domain.model;

import java.time.Instant;

/** Pago realizado a una tienda por una factura de vehículo. */
public record PagoTiendaData(
        String referenciaId,
        String contratoId,
        String tiendaId,
        double monto,
        Instant fechaPago
) {}
