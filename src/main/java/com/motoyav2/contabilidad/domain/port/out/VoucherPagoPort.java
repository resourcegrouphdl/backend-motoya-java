package com.motoyav2.contabilidad.domain.port.out;

import com.motoyav2.contabilidad.domain.model.VoucherAprobadoData;
import reactor.core.publisher.Flux;

public interface VoucherPagoPort {
    /** Devuelve todos los vouchers APROBADO. Idempotencia se maneja en el use case. */
    Flux<VoucherAprobadoData> findTodosAprobados();
}
