package com.motoyav2.cobranza.application.port.in.query;

/**
 * Parámetros de consulta para {@code GET /api/cobranzas/casos}.
 *
 * @param storeId   Obligatorio — tienda del agente autenticado.
 * @param estado    Opcional — filtra por {@code estadoCaso} (ej. EN_SEGUIMIENTO).
 * @param prioridad Opcional — ALTA | MEDIA | BAJA (calculado en memoria).
 * @param agenteId  Opcional — solo SUPERVISOR/ADMIN pueden filtrar por agente.
 * @param page      Página base-0 (default 0).
 * @param size      Tamaño de página (default 20, máx 100).
 */
public record ListarCasosQuery(
        String storeId,
        String estado,
        String prioridad,
        String agenteId,
        int page,
        int size
) {
    public ListarCasosQuery {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 20;
    }
}
