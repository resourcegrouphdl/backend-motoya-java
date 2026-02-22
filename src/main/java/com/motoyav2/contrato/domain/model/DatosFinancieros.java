package com.motoyav2.contrato.domain.model;

import java.math.BigDecimal;

public record DatosFinancieros(
        BigDecimal precioVehiculo,
        BigDecimal cuotaInicial,
        BigDecimal montoFinanciado,
        BigDecimal tasaInteresAnual,
        int numeroCuotas,
        BigDecimal cuotaMensual
) {

  public int numeroDeMeses() {
    return numeroCuotas / 2;
  }


}
