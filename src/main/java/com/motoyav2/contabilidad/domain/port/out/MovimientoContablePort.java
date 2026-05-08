package com.motoyav2.contabilidad.domain.port.out;

import com.motoyav2.contabilidad.domain.model.MovimientoContable;
import com.motoyav2.contabilidad.domain.model.TipoMovimientoContable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface MovimientoContablePort {
    Mono<Boolean> existsByReferenciaId(String referenciaId);
    Mono<Void> save(MovimientoContable movimiento);
    Flux<MovimientoContable> findByPeriodo(LocalDate desde, LocalDate hasta, String tiendaId);
    Flux<MovimientoContable> findByPeriodoYTipo(LocalDate desde, LocalDate hasta,
                                                String tiendaId, TipoMovimientoContable tipo);
}
