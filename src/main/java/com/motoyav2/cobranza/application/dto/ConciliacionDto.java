package com.motoyav2.cobranza.application.dto;

import java.util.List;

/**
 * Resultado de la conciliación entre contratos firmados/activos y casos de cobranza.
 * Permite detectar contratos que ya deberían tener un caso de cobranza pero no lo tienen.
 */
public record ConciliacionDto(
        int totalContratosFirmados,
        int totalCasosCobranza,
        int contratosSinCaso,
        List<ContratoSinCasoItem> detalle
) {
    public record ContratoSinCasoItem(
            String contratoId,
            String numeroContrato,
            String estado,
            String clienteNombre,
            String clienteTelefono,
            String clienteDni,
            String tiendaNombre,
            String fechaCreacion
    ) {}
}
