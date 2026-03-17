package com.motoyav2.evaluacion.infrastructure.adapter.in.web.dto.pipeline;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request para transicionar el estado de una solicitud.
 */
@Getter
@NoArgsConstructor
public class TransicionarEstadoRequest {
    private String nuevoEstado;    // valor exacto del enum EstadoSolicitud
    private String usuarioId;
    private String usuarioNombre;
    private String motivo;
}
