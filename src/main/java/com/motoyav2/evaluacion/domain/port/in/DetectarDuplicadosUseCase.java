package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

/**
 * Detecta si el titular o fiador de una solicitud ya tiene solicitudes activas
 * o aparece como fiador en otro crédito vigente. Persiste el resultado
 * como campo alertaDuplicado en la solicitud para que el analista lo vea.
 */
public interface DetectarDuplicadosUseCase {
    Mono<Void> detectar(String solicitudId, String titularDni, String fiadorDni);
}
