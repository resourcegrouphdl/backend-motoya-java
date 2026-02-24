package com.motoyav2.cobranza.application.dto;

/**
 * KPIs del dashboard de cobranzas.
 * Proyección del documento MetricasDocument.
 * Para rol AGENTE, se extraen los campos del sub-mapa agentes.{userId}.
 */
public record DashboardDto(
        Integer promesasVencenHoy,
        Integer promesasIncumplidas,
        Integer vouchersPendientes,
        Integer casosCriticos,
        Double moraTotal,
        Double recuperacionMes,
        Double porcentajeAutomatizado,
        Double tasaRecuperacion,
        Integer casosActivos,
        String ultimaActualizacion
) {}
