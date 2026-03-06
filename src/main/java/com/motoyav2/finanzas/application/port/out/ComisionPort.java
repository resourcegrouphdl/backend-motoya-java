package com.motoyav2.finanzas.application.port.out;

import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ComisionPort {
    Flux<ComisionVendedor> findAll(String tiendaId, LocalDate fechaInicio, LocalDate fechaFin);
    Mono<Void> marcarPagada(String comisionId);
}
