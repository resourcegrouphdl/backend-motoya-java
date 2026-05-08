package com.motoyav2.contabilidad.domain.port.out;

import com.motoyav2.contabilidad.domain.model.ContabilidadCuota;
import reactor.core.publisher.Mono;

public interface ContabilidadCuotaPort {
    Mono<ContabilidadCuota> findByContratoId(String contratoId);
    Mono<Void> save(ContabilidadCuota cuota);
}
