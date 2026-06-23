package com.motoyav2.riesgointerno.infrastructure.adapter.in.web.response;

import com.motoyav2.riesgointerno.domain.model.HistorialCambioRiesgo;
import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RegistroRiesgoDto {

    String id;
    String tipoDocumento;
    String dniRegistrado;
    String nombreRegistrado;
    List<String> telefonos;
    String tipoSujeto;
    String nivelRiesgo;
    String estadoRegistro;
    String tipoRiesgo;
    String contratoIdRelacionado;
    String solicitudIdRelacionado;
    Double montoDeudaPendiente;
    String fechaIncidente;
    String descripcion;
    List<String> evidencias;
    List<String> condicionesRehabilitacion;
    String registradoPor;
    List<HistorialDto> historialCambios;
    String fechaRegistro;
    String updatedAt;

    @Value
    @Builder
    public static class HistorialDto {
        String fecha;
        String usuario;
        String cambio;
        String motivoCambio;
    }

    public static RegistroRiesgoDto from(RegistroRiesgo r) {
        return RegistroRiesgoDto.builder()
                .id(r.getId())
                .tipoDocumento(r.getTipoDocumento() != null ? r.getTipoDocumento().name() : "DNI")
                .dniRegistrado(r.getDniRegistrado())
                .nombreRegistrado(r.getNombreRegistrado())
                .telefonos(r.getTelefonos())
                .tipoSujeto(r.getTipoSujeto() != null ? r.getTipoSujeto().name() : null)
                .nivelRiesgo(r.getNivelRiesgo() != null ? r.getNivelRiesgo().name() : null)
                .estadoRegistro(r.getEstadoRegistro() != null ? r.getEstadoRegistro().name() : null)
                .tipoRiesgo(r.getTipoRiesgo() != null ? r.getTipoRiesgo().name() : null)
                .contratoIdRelacionado(r.getContratoIdRelacionado())
                .solicitudIdRelacionado(r.getSolicitudIdRelacionado())
                .montoDeudaPendiente(r.getMontoDeudaPendiente())
                .fechaIncidente(r.getFechaIncidente() != null ? r.getFechaIncidente().toDate().toInstant().toString() : null)
                .descripcion(r.getDescripcion())
                .evidencias(r.getEvidencias())
                .condicionesRehabilitacion(r.getCondicionesRehabilitacion())
                .registradoPor(r.getRegistradoPor())
                .historialCambios(mapHistorial(r.getHistorialCambios()))
                .fechaRegistro(r.getFechaRegistro() != null ? r.getFechaRegistro().toDate().toInstant().toString() : null)
                .updatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt().toDate().toInstant().toString() : null)
                .build();
    }

    private static List<HistorialDto> mapHistorial(List<HistorialCambioRiesgo> historial) {
        if (historial == null) return List.of();
        return historial.stream()
                .map(h -> HistorialDto.builder()
                        .fecha(h.getFecha() != null ? h.getFecha().toDate().toInstant().toString() : null)
                        .usuario(h.getUsuario())
                        .cambio(h.getCambio())
                        .motivoCambio(h.getMotivoCambio())
                        .build())
                .toList();
    }
}
