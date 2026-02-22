package com.motoyav2.contrato.domain.model;

import com.motoyav2.contrato.domain.enums.TipoDocumentoGenerado;
import lombok.Builder;

import java.time.Instant;
@Builder
public record DocumentoGenerado(
  String id,
  TipoDocumentoGenerado tipo,
  String urlDocumento,
  String nombreArchivo,
  Instant fechaGeneracion,
  String generadoPor,
  Integer versionDocumento,
  String descargadoPor,
  Instant fechaDescarga

) {
}
