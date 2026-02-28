package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.BoucherPagoInicial;
import com.motoyav2.contrato.domain.model.Contrato;
import reactor.core.publisher.Mono;

public interface SubirBoucherUseCase {
    Mono<Contrato> subir(String contratoId, BoucherPagoInicial boucher);
}
