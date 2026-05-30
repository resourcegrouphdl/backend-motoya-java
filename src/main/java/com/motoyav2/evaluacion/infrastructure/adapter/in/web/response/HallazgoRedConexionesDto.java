package com.motoyav2.evaluacion.infrastructure.adapter.in.web.response;

import com.motoyav2.evaluacion.domain.model.HallazgoRedConexiones;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class HallazgoRedConexionesDto {

    String telefono;
    String severidad;
    String descripcion;
    String rolEnExpediente;
    List<SolicitudReferenciadaDto> solicitudesRelacionadas;
    /** Presente solo para hallazgos de lista negra interna. */
    String origen;

    @Value
    @Builder
    public static class SolicitudReferenciadaDto {
        String solicitudId;
        String codigoSolicitud;
        String rolEncontrado;
        String estado;
    }

    public static HallazgoRedConexionesDto from(HallazgoRedConexiones h) {
        List<SolicitudReferenciadaDto> refs = h.getSolicitudesRelacionadas().stream()
                .map(r -> SolicitudReferenciadaDto.builder()
                        .solicitudId(r.getSolicitudId())
                        .codigoSolicitud(r.getCodigoSolicitud())
                        .rolEncontrado(r.getRolEncontrado())
                        .estado(r.getEstado())
                        .build())
                .toList();
        return HallazgoRedConexionesDto.builder()
                .telefono(h.getTelefono())
                .severidad(h.getSeveridad())
                .descripcion(h.getDescripcion())
                .rolEnExpediente(h.getRolEnExpediente())
                .solicitudesRelacionadas(refs)
                .origen(h.getOrigen())
                .build();
    }
}
