package com.motoyav2.contabilidad.domain.port.out;

import com.motoyav2.contabilidad.domain.model.SnapshotCartera;
import reactor.core.publisher.Flux;

public interface CarteraPort {

    /**
     * Retorna un {@link SnapshotCartera} por cada contrato activo encontrado.
     * La agregación final es responsabilidad del use case.
     */
    Flux<SnapshotCartera> findAllCasos(String tiendaId);
}
