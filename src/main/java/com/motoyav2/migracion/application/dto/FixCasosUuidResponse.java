package com.motoyav2.migracion.application.dto;

import java.util.List;

public record FixCasosUuidResponse(
        int totalRevisados,
        int corregidos,
        int omitidosPorConflicto,
        int omitidosSinContratoId,
        int errores,
        List<String> detalle
) {}
