package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.Contrato;
import reactor.core.publisher.Mono;

public interface AprobarContratoUseCase {

    Mono<Contrato> aprobar(String contratoId, String aprobadoPor);
}
