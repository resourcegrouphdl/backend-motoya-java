package com.motoyav2.contabilidad.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class ResumenIgv {

    LocalDate desde;
    LocalDate hasta;
    Double totalSubTotal;
    Double totalIgv;
    Double totalBruto;
    int cantidadBoletas;
    int cantidadFacturas;
    int cantidadAnulados;
}
