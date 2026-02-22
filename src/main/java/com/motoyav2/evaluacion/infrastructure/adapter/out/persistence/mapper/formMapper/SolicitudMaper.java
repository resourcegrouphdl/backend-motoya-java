package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper;

import com.motoyav2.evaluacion.domain.enums.EstadoDeLaEtapa;
import com.motoyav2.evaluacion.domain.enums.PrioridadDeatencion;
import com.motoyav2.evaluacion.domain.model.Evaluacion;
import com.motoyav2.evaluacion.domain.model.ExpedienteSeed;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.FirebaseSolicitud;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.firebaseform.VendedorFirebase;

import java.util.List;

public final class SolicitudMaper {

  private SolicitudMaper() {
  }

  public static ExpedienteSeed toSeed(FirebaseSolicitud doc) {

    VendedorFirebase v = doc.getVendedor();

    Evaluacion evaluacion = Evaluacion.builder()
        .estado(EstadoDeLaEtapa.PENDIENTE.name())
        .etapa(EstadoDeLaEtapa.PENDIENTE)
        .prioridad(PrioridadDeatencion.MEDIA)
        .progresoPorcentaje(0)
        .solicitudFirebaseId(doc.getFormularioId())
        .codigoDeSolicitud(doc.getCodigoDeSolicitud())
        .creadoEn(doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null)
        .titularId(doc.getTitularId())
        .fiadorId(doc.getFiadorId())
        .mensajeOpcional(doc.getMensajeOpcional())
        .vehiculoId(doc.getVehiculoId())
        .vendedorId(v != null ? v.getId() : null)
        .vendedorNombre(v != null ? v.getNombreVendedor() : null)
        .tiendaId(v != null && v.getTienda() != null && !v.getTienda().isEmpty()
            ? v.getTienda().getFirst() : null)
        .version(1)
        .build();

    List<String> referenciasIds = doc.getReferenciasIds() != null
        ? doc.getReferenciasIds()
        : List.of();

    return new ExpedienteSeed(
        evaluacion,
        referenciasIds,
        doc.getMontoCuota() != null ? doc.getMontoCuota().toString() : null,
        doc.getPlazoQuincenas() != null ? doc.getPlazoQuincenas().toString() : null,
        doc.getPrecioCompraMoto() != null ? doc.getPrecioCompraMoto().toString() : null
    );
  }

}
