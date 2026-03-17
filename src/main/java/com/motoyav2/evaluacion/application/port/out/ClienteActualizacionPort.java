package com.motoyav2.evaluacion.application.port.out;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Puerto de salida para actualizaciones parciales en clientes_v1.
 * Permite escribir evaluacionDocumentos, scores y estadoValidacion
 * sin reemplazar el documento completo.
 */
public interface ClienteActualizacionPort {

    /**
     * Actualiza el mapa evaluacionDocumentos en clientes_v1/{clienteId}.
     *
     * @param clienteId ID del documento en clientes_v1
     * @param evaluacionDocumentos mapa completo actualizado
     * @param estadoValidacion nuevo estadoValidacionDocumentos
     * @param documentosObservados lista de tipos de documento observados
     */
    Mono<Void> actualizarEvaluacionDocumentos(String clienteId,
                                               Map<String, Object> evaluacionDocumentos,
                                               String estadoValidacion,
                                               java.util.List<String> documentosObservados);
}
