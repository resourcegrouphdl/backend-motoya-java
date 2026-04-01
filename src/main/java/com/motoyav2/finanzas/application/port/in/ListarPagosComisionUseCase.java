package com.motoyav2.finanzas.application.port.in;

import com.motoyav2.finanzas.domain.model.PagoComisionVendedor;
import reactor.core.publisher.Flux;

public interface ListarPagosComisionUseCase {
    Flux<PagoComisionVendedor> ejecutar(String vendedorId, String tiendaId, String estado);
}
