package com.motoyav2.evaluacion.domain.port.in;

import com.motoyav2.evaluacion.domain.model.HallazgoRedConexiones;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Cruza los teléfonos del expediente contra solicitudes y referencias históricas
 * para detectar redes de contactos compartidos entre créditos distintos.
 */
public interface AnalizarRedConexionesUseCase {
    Mono<List<HallazgoRedConexiones>> analizar(String solicitudId);
}
