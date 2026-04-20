package com.motoyav2.evaluacion.domain.port.out;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Acceso a la colección `personas` — registro maestro para autocomplete
 * y central de riesgo. NO se usa para generación de certificados ni
 * para leer datos de evaluación (para eso están los snapshots en `clientes_v1`).
 */
public interface PersonaRepository {

    /**
     * Crea o actualiza el registro maestro de una persona (upsert por documentNumber).
     * Solo persiste datos de contacto básicos; no sobreescribe evaluaciones ni documentos.
     */
    Mono<Void> upsert(String documentNumber, Map<String, Object> fields);

    /**
     * Busca el registro maestro de una persona por número de documento.
     * Retorna vacío si no existe historial previo.
     */
    Mono<Map<String, Object>> findByDocumentNumber(String documentNumber);
}
