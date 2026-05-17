package com.motoyav2.contabilidad.domain.port.in;

import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface GenerarExcelContratosUseCase {
    Mono<byte[]> generarExcel(LocalDate desde, LocalDate hasta, String tiendaId);
}
