package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper;

import com.motoyav2.evaluacion.domain.model.Vehiculo;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.FirebaseVehiculo;

public final class VehiculoFirebaseMapper {

  private VehiculoFirebaseMapper() {
  }

  public static Vehiculo toDomain(FirebaseVehiculo doc) {
    return Vehiculo.builder()
        .id(doc.getId())
        .marca(doc.getMarca())
        .modelo(doc.getModelo())
        .anio(doc.getAnio())
        .color(doc.getColor())
        .descripcionCompleta(buildDescripcion(doc))
        .firebaseVehiculoId(doc.getId())
        .creadoEn(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
        .actualizadoEn(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null)
        .build();
  }

  private static String buildDescripcion(FirebaseVehiculo doc) {
    StringBuilder sb = new StringBuilder();
    if (doc.getMarca() != null) sb.append(doc.getMarca());
    if (doc.getModelo() != null) sb.append(" ").append(doc.getModelo());
    if (doc.getAnio() != null) sb.append(" ").append(doc.getAnio());
    if (doc.getColor() != null) sb.append(" ").append(doc.getColor());
    return sb.toString().trim();
  }

}
