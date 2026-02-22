package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VehiculoDocument {

  private String id;
  private String marca;
  private String modelo;
  private String anio;
  private String color;
  private String cilindrada;
  private String descripcionCompleta;
  private String precioReferencial;
  private String firebaseVehiculoId;
  private String creadoEn;
  private String actualizadoEn;

}
