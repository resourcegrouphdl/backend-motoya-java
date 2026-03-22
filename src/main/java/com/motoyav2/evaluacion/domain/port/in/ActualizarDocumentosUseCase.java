package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface ActualizarDocumentosUseCase {
    /**
     * Reemplaza los archivos del titular cuando la solicitud está en
     * documentos_observados o documentos_incompletos.
     * El estado NO cambia — el admin decide avanzar tras revisar.
     */
    /**
     * @param clienteId ID del cliente cuyos archivos se actualizan.
     *                  Si es null se usará el titularId de la solicitud.
     */
    Mono<Void> ejecutar(String solicitudId, Map<String, String> archivos, String clienteId);
}
