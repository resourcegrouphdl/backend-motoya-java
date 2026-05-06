package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.OperacionBancariaIndexDocument;
import reactor.core.publisher.Mono;

public interface OperacionBancariaIndexPort {

    /**
     * Intenta registrar la operación usando semántica create() de Firestore.
     * @return {@code true} si fue registrada (es nueva), {@code false} si ya existía (duplicado)
     */
    Mono<Boolean> registrarSiNueva(String banco, String numeroOperacion, OperacionBancariaIndexDocument datos);

    /**
     * Busca la entrada del índice para banco + numeroOperacion.
     * Retorna Mono vacío si no existe.
     */
    Mono<OperacionBancariaIndexDocument> buscarDuplicado(String banco, String numeroOperacion);

    /**
     * Elimina la entrada del índice. Usado en rollback de saga si procesarAprobacion falla.
     */
    Mono<Void> eliminar(String banco, String numeroOperacion);
}