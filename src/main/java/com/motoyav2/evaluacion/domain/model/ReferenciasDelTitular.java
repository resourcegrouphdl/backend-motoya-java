package com.motoyav2.evaluacion.domain.model;

import com.motoyav2.evaluacion.domain.enums.ResultadoDeVerificacion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReferenciasDelTitular {

  private String id;
  private String evaluacionId;
  private String codigoDeSolicitud;

  // datos de la referencia
  private Integer numero;
  private String nombre;
  private String apellidos;
  private String nombreCompleto;
  private String telefono;
  private String parentesco;

  // verificacion
  private Boolean verificada;
  private ResultadoDeVerificacion resultadoDeVerificacion;
  private Instant fechaDeVerificacion;
  private String verificadoPor;
  private String nombreDelVerificador;
  private String observaciones;
  private String scoreDeVerificacion;

  // reagendamiento
  private String pospuesto;
  private String reAgendadoPara;

  // auditoria
  private String creadoEn;
  private String actualizadoEn;

}
