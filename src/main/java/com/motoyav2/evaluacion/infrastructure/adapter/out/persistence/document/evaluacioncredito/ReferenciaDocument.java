package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReferenciaDocument {

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
  private String resultadoDeVerificacion;
  private String fechaDeVerificacion;
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
