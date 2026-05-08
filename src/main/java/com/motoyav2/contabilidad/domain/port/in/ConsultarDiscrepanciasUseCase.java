package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.DiscrepanciaVoucher;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface ConsultarDiscrepanciasUseCase {

    Flux<DiscrepanciaVoucher> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId);
}
