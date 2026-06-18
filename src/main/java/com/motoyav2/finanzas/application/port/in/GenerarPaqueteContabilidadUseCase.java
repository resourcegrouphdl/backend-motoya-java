package com.motoyav2.finanzas.application.port.in;

import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface GenerarPaqueteContabilidadUseCase {
    Mono<byte[]> ejecutar(LocalDate fechaInicio, LocalDate fechaFin);
}
