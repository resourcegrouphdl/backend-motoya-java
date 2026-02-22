package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FinanciamientoDocument {

  private String id;
  private String evaluacionId;

  // costos base
  private String montoDelVehiculo;
  private String soat;
  private String costosNotariales;
  private String costoTotal;

  // financiamiento original
  private String inicialOriginal;
  private String montoAFinanciarOriginal;
  private String numeroCuotasQuincenales;
  private String montoCuotaQuincenal;

  // financiamiento ajustado
  private String inicialAjustada;
  private String montoAFinanciarAjustado;
  private String montoCuotaAjustada;

  // tasa de interes
  private String tasaDeInteresMensual;
  private String tasaDeInteresAnual;

  // indicadores calculados
  private String porcentajeInicial;
  private String relacionCuotaIngreso;
  private String capacidadDePago;

  // auditoria
  private String creadoEn;
  private String actualizadoEn;

}
