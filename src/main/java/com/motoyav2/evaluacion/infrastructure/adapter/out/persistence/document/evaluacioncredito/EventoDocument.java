package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventoDocument {

  private String id;
  private String evaluacionId;
  private String codigoDeSolicitud;

  // tipo de evento
  private String tipoEvento;

  // payload del evento
  private String payload;

  // version
  private Integer version;

  // usuario que genero el evento
  private String usuarioId;
  private String usuarioNombre;

  // metadata
  private String ipOrigen;
  private String userAgent;

  // timestamp inmutable
  private String timestamp;

}
