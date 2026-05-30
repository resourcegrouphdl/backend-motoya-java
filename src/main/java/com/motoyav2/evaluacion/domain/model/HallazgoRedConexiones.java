package com.motoyav2.evaluacion.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class HallazgoRedConexiones {

    String telefono;
    /** "CRITICA" | "ALTA" | "MEDIA" */
    String severidad;
    String descripcion;
    /** Rol dentro del expediente actual: "Titular", "Aval", "Referencia 1", etc. */
    String rolEnExpediente;
    List<SolicitudReferenciada> solicitudesRelacionadas;
    /** Si viene de lista negra interna: "Lista Negra Interna". Null para hallazgos históricos. */
    String origen;

    @Value
    @Builder
    public static class SolicitudReferenciada {
        String solicitudId;
        String codigoSolicitud;
        /** "titular en otra solicitud" | "aval en otra solicitud" | "referencia (parentesco)" */
        String rolEncontrado;
        String estado;
    }
}
