package com.motoyav2.cobranza.application.dto;

/**
 * Proyección enriquecida de una promesa para la vista global de gestión.
 * Incluye datos del caso (clienteNombre, saldoActual) para evitar llamadas extra.
 */
public record PromesaResumenDto(
        String id,
        String contratoId,
        String clienteNombre,
        Double saldoActual,
        String fecha,
        Double monto,
        String estado,
        String observaciones
) {}
