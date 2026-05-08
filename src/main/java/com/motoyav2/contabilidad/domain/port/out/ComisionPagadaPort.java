package com.motoyav2.contabilidad.domain.port.out;

import com.motoyav2.contabilidad.domain.model.ComisionPagadaData;
import reactor.core.publisher.Flux;

public interface ComisionPagadaPort {
    /** Devuelve todas las comisiones de vendedor en estado PAGADO. */
    Flux<ComisionPagadaData> findTodosPagadas();
}
