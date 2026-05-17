package com.motoyav2.contabilidad.domain.port.in;

import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface GenerarPdfLiquidacionUseCase {
    Mono<byte[]> generarPdf(LocalDate desde, LocalDate hasta, String tiendaId);
}
