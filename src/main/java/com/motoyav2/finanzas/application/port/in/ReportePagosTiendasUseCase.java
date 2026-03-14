package com.motoyav2.finanzas.application.port.in;

import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ReportePagosTiendasUseCase {
    Mono<byte[]> generarReportePagosTiendas(LocalDate fechaInicio, LocalDate fechaFin, String formato);
}
