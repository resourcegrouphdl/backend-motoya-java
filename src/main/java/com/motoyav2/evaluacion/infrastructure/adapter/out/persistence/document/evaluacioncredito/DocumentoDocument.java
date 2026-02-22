package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentoDocument {

  private String id;
  private String codigoDeSolicitud;
  private String evaluacionId;

  // tipo y persona
  private String tipoDocumento;
  private String tipoPersona;
  private String nombre;

  // estado de validacion
  private String estado;
  private Boolean validado;

  // url del archivo
  private String url;

  // validacion
  private String validadoPor;
  private String fechaDeValidacion;
  private String observaciones;

  // auditoria
  private String creadoEn;
  private String actualizadoEn;

}
