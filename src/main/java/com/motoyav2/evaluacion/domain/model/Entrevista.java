package com.motoyav2.evaluacion.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Entrevista {

  private String id;
  private String evaluacionId;
  private String codigoDeSolicitud;
  private String tipoPersona;

  // evaluacion de actitud (1-5)
  private Integer actitud;
  private Integer disposicion;
  private Integer claridad;

  // evaluacion de estabilidad (1-5)
  private Integer estabilidadLaboral;
  private Integer estabilidadDomiciliaria;
  private Integer historicoCrediticio;

  // capacidad economica
  private Boolean ingresoVerificable;
  private Boolean comprobanteIngresos;
  private BigDecimal gastosMensuales;
  private BigDecimal capacidadPagoCalculada;

  // score y recomendacion
  private BigDecimal scoreTotal;
  private String recomendacion;

  // observaciones
  private String observaciones;

  // metadata de la entrevista
  private Instant fechaEntrevista;
  private Integer duracionMinutos;
  private String realizadoPor;
  private String nombreEntrevistador;

  // campos adicionales de gestion
  private String agendadoParaLaFecha;
  private Boolean establecerAlerta;
  private String salaDeGoogleTeams;

  // auditoria
  private String creadoEn;
  private String actualizadoEn;

}
