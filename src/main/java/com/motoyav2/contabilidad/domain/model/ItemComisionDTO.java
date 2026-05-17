package com.motoyav2.contabilidad.domain.model;

import java.math.BigDecimal;

public record ItemComisionDTO(
        String comisionId,
        String contratoId,
        String clienteNombre,
        String clienteDocumento,
        BigDecimal montoComision,
        String estado,
        String fecha
) {}
