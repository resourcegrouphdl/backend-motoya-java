package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertaDocument {

  private String id;
  private String evaluacionId;
  private String codigoDeSolicitud;

  // tipo y severidad
  private String tipo;
  private String severidad;

  // contenido
  private String mensaje;
  private String descripcion;

  // estado
  private Boolean resuelta;

  // resolucion
  private String fechaDeResolucion;
  private String resueltaPor;
  private String resolucionNota;

  // auditoria
  private String creadoEn;
  private String actualizadoEn;

}
