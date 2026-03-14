package com.motoyav2.finanzas.application.port.in;

import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ExportarComisionesUseCase {
    Mono<byte[]> ejecutar(String tiendaId, LocalDate fechaInicio, LocalDate fechaFin);
}