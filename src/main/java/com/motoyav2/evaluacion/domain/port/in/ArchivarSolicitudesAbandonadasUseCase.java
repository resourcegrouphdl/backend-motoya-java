package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

/**
 * Archiva automáticamente solicitudes que llevan más de N días sin actividad
 * en estados tempranos del pipeline. Notifica al vendedor por WhatsApp.
 */
public interface ArchivarSolicitudesAbandonadasUseCase {
    Mono<Integer> archivar(int diasInactividad);
}
