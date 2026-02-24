package com.motoyav2.cobranza.application.dto;

/**
 * Contadores de alertas no descartadas, agrupados por nivel.
 */
public record AlertasResumenDto(
        long totalCriticas,
        long totalWarnings,
        long totalInfo,
        long totalNoLeidas
) {}
