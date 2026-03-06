package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface ListarComisionesUseCase {
    Flux<ComisionVendedor> ejecutar(String tiendaId, LocalDate fechaInicio, LocalDate fechaFin);
}
