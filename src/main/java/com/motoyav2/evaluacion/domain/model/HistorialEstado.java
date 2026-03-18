package com.motoyav2.evaluacion.domain.model;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HistorialEstado {
    String id;
    String solicitudId;
    EstadoSolicitud estadoAnterior;
    EstadoSolicitud estadoNuevo;
    Timestamp fechaCambio;
    String usuarioId;
    String usuarioNombre;
    String motivo;
}
