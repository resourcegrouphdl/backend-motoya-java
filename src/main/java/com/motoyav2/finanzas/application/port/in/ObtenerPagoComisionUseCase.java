package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.domain.model.PagoComisionVendedor;
import reactor.core.publisher.Mono;

public interface ObtenerPagoComisionUseCase {
    Mono<PagoComisionVendedor> ejecutar(String id);
}
