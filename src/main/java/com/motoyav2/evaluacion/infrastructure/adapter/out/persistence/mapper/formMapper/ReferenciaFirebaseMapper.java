package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper;

import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.FirebaseReferencias;

public final class ReferenciaFirebaseMapper {

  private ReferenciaFirebaseMapper() {
  }

  public static ReferenciasDelTitular toDomain(FirebaseReferencias doc, int numero) {
    String nombreCompleto = buildNombreCompleto(doc);
    // El numero puede venir del campo o de la posición en la lista
    int numFinal = doc.getNumero() != null ? doc.getNumero() : numero;

    return ReferenciasDelTitular.builder()
        .numero(numFinal)
        .nombre(doc.getNombre())
        .apellidos(doc.getApellidos())
        .nombreCompleto(nombreCompleto)
        .telefono(doc.getTelefono())
        .parentesco(doc.getParentesco())
        .codigoDeSolicitud(doc.getCodigoDeSolicitud())
        .estadoVerificacion(doc.getEstadoVerificacion())
        .resultadoContacto(doc.getResultadoContacto())
        .verificada("verificado".equalsIgnoreCase(doc.getEstadoVerificacion()))
        .scoreDeVerificacionNum(doc.getScoreVerificacion())
        .scoreDeVerificacion(doc.getScoreVerificacion() != null ? String.valueOf(doc.getScoreVerificacion()) : null)
        .scoreMaximo(doc.getScoreMaximo())
        .calificacion(doc.getCalificacion())
        .actitudDuranteContacto(doc.getActitudDuranteContacto())
        .observaciones(doc.getObservaciones())
        .rechazada(doc.getRechazada())
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
