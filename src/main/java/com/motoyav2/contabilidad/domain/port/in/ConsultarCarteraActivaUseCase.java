package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.SnapshotCartera;
import reactor.core.publisher.Mono;

public interface ConsultarCarteraActivaUseCase {

    Mono<SnapshotCartera> ejecutar(String tiendaId);
}
