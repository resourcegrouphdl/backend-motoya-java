package com.motoyav2.contabilidad.domain.model;

import java.util.List;

public record ContratosTiendaDTO(
        String tiendaId,
        String tiendaNombre,
        List<ContratoReporteRow> contratos,
        int total
) {}
