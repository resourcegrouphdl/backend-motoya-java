package com.motoyav2.contrato.domain.model;

import com.motoyav2.contrato.domain.enums.EstadoContrato;
import com.motoyav2.contrato.domain.enums.FaseContrato;

import java.time.Instant;

public record ContratoListItem(
        String id,
        String numeroContrato,
        EstadoContrato estado,
        FaseContrato fase,
        String nombreTitular,
        String documentoTitular,
        String tiendaNombre,
        Instant fechaCreacion
) {
}
