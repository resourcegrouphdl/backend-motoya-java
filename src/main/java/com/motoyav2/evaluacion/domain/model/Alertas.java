package com.motoyav2.evaluacion.domain.model;

import com.motoyav2.evaluacion.domain.enums.Severidad;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Alertas {

  private String id;
  private String evaluacionId;
  private String codigoDeSolicitud;

  // tipo y severidad
  private String tipo;
  private Severidad severidad;

  // contenido
  private String mensaje;
  private String descripcion;

  // estado
  private Boolean resuelta;

  // resolucion
  private Instant fechaDeResolucion;
  private String resueltaPor;
  private String resolucionNota;

  // auditoria
  private String creadoEn;
  private String actualizadoEn;

}
