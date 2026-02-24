package com.motoyav2.cobranza.application.dto;

/**
 * Proyección liviana para el listado de casos.
 * diasMora y prioridad se calculan en tiempo real — no se almacenan en Firestore.
 */
public record CasoResumenDto(
        String contratoId,
        String cliente,
        int diasMora,
        Double deuda,
        Double saldoActual,
        String nivelEstrategia,
        String prioridad,
        String estado,
        String ultimaAccion,
        String ultimaGestion,
        String proximaAccion,
        String agenteAsignado,
        String telefono
) {}
