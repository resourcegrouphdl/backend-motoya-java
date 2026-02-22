package com.motoyav2.evaluacion.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Vehiculo {

  private String id;
  private String marca;
  private String modelo;
  private String anio;
  private String color;
  private String cilindrada;
  private String descripcionCompleta;

  // precio
  private String precioReferencial;

  // metadata
  private String firebaseVehiculoId;

  // auditoria
  private String creadoEn;
  private String actualizadoEn;

}
