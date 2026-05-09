package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import reactor.core.publisher.Flux;

public interface ObtenerMisComisionesUseCase {
    Flux<ComisionVendedor> ejecutar(String vendedorId);
}
