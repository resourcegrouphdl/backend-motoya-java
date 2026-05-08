package com.motoyav2.contabilidad.domain.port.in;

import reactor.core.publisher.Mono;

public interface SincronizarContabilidadUseCase {
    /** Sincronización incremental: solo eventos nuevos desde última ejecución. */
    Mono<Integer> sincronizarIncremental();

    /** Backfill completo: procesa todo el histórico desde el principio. */
    Mono<Integer> sincronizarHistorico();
}
