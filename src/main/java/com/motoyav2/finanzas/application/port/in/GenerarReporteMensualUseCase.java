package com.motoyav2.finanzas.application.port.in;

import reactor.core.publisher.Mono;

public interface GenerarReporteMensualUseCase {
    /** @param mes formato "2026-03" */
    Mono<String> ejecutar(String mes);
}
