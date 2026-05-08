package com.motoyav2.contabilidad.domain.port.out;

import com.motoyav2.contabilidad.domain.model.PagoTiendaData;
import reactor.core.publisher.Flux;

public interface FacturaTiendaPort {
    /** Devuelve todos los pagos a tienda en estado PAGADO. */
    Flux<PagoTiendaData> findTodosPagados();
}
