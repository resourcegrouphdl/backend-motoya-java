package com.motoyav2.migracion.application.dto;

import java.util.List;

public record EjecutarLoteResponse(
        int migrados,
        int errores,
        List<DetalleItem> detalle
) {
    public record DetalleItem(
            String id,
            String status,
            String contratoId,
            String error
    ) {}
}
