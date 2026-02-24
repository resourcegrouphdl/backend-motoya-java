package com.motoyav2.cobranza.application.port.out;

import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MovimientoDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida para el ledger de deuda — APPEND ONLY.
 */
public interface MovimientoPort {

    Mono<MovimientoDocument> append(String contratoId, MovimientoDocument movimiento);

    Flux<MovimientoDocument> findByContratoId(String contratoId);
}
