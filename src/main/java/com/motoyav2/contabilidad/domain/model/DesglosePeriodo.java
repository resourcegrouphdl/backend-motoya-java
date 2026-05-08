package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

/**
 * Desglose de ingresos por período: separa capital, interés,
 * costos de tienda y comisiones para el informe contable.
 */
@Value
@Builder
public class DesglosePeriodo {
    String periodo;
    int cantidadCobros;
    double montoTotalCobrado;
    double montoCapital;
    double montoInteres;
    double costosTienda;
    double costosComision;
    double utilidadBruta;
    double utilidadNeta;
}
