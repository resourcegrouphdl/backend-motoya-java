package com.motoyav2.contabilidad.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReporteLiquidacionResponse(
        LocalDate desde,
        LocalDate hasta,
        List<LiquidacionTiendaDTO> tiendas,
        BigDecimal totalGeneral,
        int totalItems
) {}
