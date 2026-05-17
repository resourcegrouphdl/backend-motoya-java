package com.motoyav2.contabilidad.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record LiquidacionTiendaDTO(
        String tiendaId,
        String tiendaNombre,
        List<LiquidacionVendedorDTO> vendedores,
        BigDecimal subtotal
) {}
