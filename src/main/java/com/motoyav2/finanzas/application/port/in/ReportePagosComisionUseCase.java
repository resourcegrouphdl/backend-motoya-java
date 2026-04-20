package com.motoyav2.finanzas.application.port.in;

import reactor.core.publisher.Mono;

public interface ReportePagosComisionUseCase {
    Mono<byte[]> generarReportePagosComision(String tiendaId, String estado, String formato);
}
