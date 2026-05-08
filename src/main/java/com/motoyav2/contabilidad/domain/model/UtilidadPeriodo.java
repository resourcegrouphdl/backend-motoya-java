package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

/**
 * Resumen ejecutivo de utilidad para el período solicitado.
 */
@Value
@Builder
public class UtilidadPeriodo {
    String desde;
    String hasta;
    String tiendaId;
    double totalIngresos;
    double totalCapitalRecuperado;
    double totalInteresGanado;
    double totalCostosTienda;
    double totalCostosComision;
    double utilidadBruta;
    double utilidadNeta;
    double margenNeto;
    int cantidadCobros;
    int cantidadPagosTienda;
    int cantidadComisionesPagadas;
}
