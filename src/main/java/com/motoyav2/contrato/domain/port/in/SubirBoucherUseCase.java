package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.BoucherPagoInicial;
import reactor.core.publisher.Mono;

public interface SubirBoucherUseCase {
    Mono<BoucherPagoInicial> subir(String contratoId, BoucherPagoInicial boucher);
}
