package com.motoyav2.contrato.infrastructure.adapter.in.web.dto;

import java.time.Instant;

public record ContratoListItemDto(
        String id,
        String numeroContrato,
        String estado,
        String fase,
        String nombreTitular,
        String documentoTitular,
        String tiendaNombre,
        Instant fechaCreacion
) {
}

/*
        String id,
        String numeroContrato,
        String nombreCompleto,
        String numeroDocumento,
        String tiendaNombre,
      --  String montoFinanciado,
        String estado,
        String faseActual,
        Instant fechaCreacion
) {
 */