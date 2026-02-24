package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.dto.Vista360CasoDto;
import reactor.core.publisher.Mono;

public interface ObtenerCasoUseCase {

    /** Retorna la Vista 360 del caso: caso + eventos + movimientos + promesas + acuerdos */
    Mono<Vista360CasoDto> ejecutar(String contratoId);
}
