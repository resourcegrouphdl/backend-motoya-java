package com.motoyav2.contabilidad.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record LiquidacionVendedorDTO(
        String vendedorId,
        String vendedorNombre,
        String vendedorDocumento,
        int totalVentas,
        BigDecimal totalComision,
        List<ItemComisionDTO> items
) {}
