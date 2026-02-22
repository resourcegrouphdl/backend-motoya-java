package com.motoyav2.evaluacion.domain.model;

import com.motoyav2.evaluacion.domain.enums.EstadoDeLaEtapa;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EtapaDeEvaluacion {

  private String id;
  private String evaluacionId;
  private String codigoDeSolicitud;

  // etapa
  private String numero;
  private String nombre;
  private String descripcion;

  // estado
  private EstadoDeLaEtapa estado;

  // tiempos
  private String fechaInicio;
  private String fechaFin;

  // quien completo
  private String completadoPor;
  private String nombreCompletador;
  private String observaciones;

  // auditoria
  private String creadoEn;
  private String actualizadoEn;

}
