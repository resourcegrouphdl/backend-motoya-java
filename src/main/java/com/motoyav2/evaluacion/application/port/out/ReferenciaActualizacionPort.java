package com.motoyav2.evaluacion.application.port.out;

import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * Puerto de salida para actualizar parcialmente un documento en la colección referencias.
 */
public interface ReferenciaActualizacionPort {

    Mono<Void> actualizarVerificacion(String referenciaId, Map<String, Object> campos);
}
