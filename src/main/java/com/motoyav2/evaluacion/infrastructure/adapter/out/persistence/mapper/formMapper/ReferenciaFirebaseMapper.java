package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper;

import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.FirebaseReferencias;

public final class ReferenciaFirebaseMapper {

  private ReferenciaFirebaseMapper() {
  }

  public static ReferenciasDelTitular toDomain(FirebaseReferencias doc, int numero) {
    String nombreCompleto = buildNombreCompleto(doc);
    return ReferenciasDelTitular.builder()
        .numero(numero)
        .nombre(doc.getNombre())
        .apellidos(doc.getApellidos())
        .nombreCompleto(nombreCompleto)
        .telefono(doc.getTelefono())
        .parentesco(doc.getParentesco())
        .codigoDeSolicitud(doc.getCodigoDeSolicitud())
        .verificada(false)
        .creadoEn(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
        .actualizadoEn(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null)
        .build();
  }

  private static String buildNombreCompleto(FirebaseReferencias doc) {
    StringBuilder sb = new StringBuilder();
    if (doc.getNombre() != null) sb.append(doc.getNombre());
    if (doc.getApellidos() != null) sb.append(" ").append(doc.getApellidos());
    return sb.toString().trim();
  }

}
