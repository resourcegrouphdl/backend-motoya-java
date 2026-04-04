package com.motoyav2.migracion.application.dto;

import java.util.List;

/**
 * Resultado del barrido de contratos → cobranzas-casos.
 */
public record BarridoContratoResponse(
        int total,
        int creados,
        int actualizados,
        int omitidos,
        int errores,
        List<DetalleItem> detalles
) {
    public record DetalleItem(
            String contratoId,
            /** CREADO | ACTUALIZADO | OMITIDO | SIN_CRONOGRAMA | ERROR */
            String accion,
            String detalle
    ) {}
}
