package com.motoyav2.finanzas.application.port.in;

import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ReporteComisionesUseCase {
    Mono<byte[]> generarReporteComisiones(LocalDate fechaInicio, LocalDate fechaFin, String formato);
}
