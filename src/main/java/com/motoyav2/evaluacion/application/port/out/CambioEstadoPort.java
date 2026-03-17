package com.motoyav2.evaluacion.application.port.out;

import com.motoyav2.evaluacion.domain.enums.EstadoSolicitud;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida para registrar cambios de estado en cambios_estado_solicitud.
 */
public interface CambioEstadoPort {

    Mono<Void> registrar(String solicitudId,
                          EstadoSolicitud estadoAnterior,
                          EstadoSolicitud estadoNuevo,
                          String usuarioId,
                          String usuarioNombre,
                          String motivo);
}
