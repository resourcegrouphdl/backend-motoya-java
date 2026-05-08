package com.motoyav2.contabilidad.domain.port.in;

import com.motoyav2.contabilidad.domain.model.ComprobanteContable;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

public interface ConsultarComprobantesUseCase {

    Flux<ComprobanteContable> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId, String tipo);
}
