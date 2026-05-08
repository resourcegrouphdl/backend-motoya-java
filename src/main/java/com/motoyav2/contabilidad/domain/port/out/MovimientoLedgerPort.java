package com.motoyav2.contabilidad.domain.port.out;

import com.motoyav2.contabilidad.domain.model.LineaIngreso;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface MovimientoLedgerPort {

    Flux<LineaIngreso> findPagosByPeriodo(LocalDate desde, LocalDate hasta, String tiendaId);
}
