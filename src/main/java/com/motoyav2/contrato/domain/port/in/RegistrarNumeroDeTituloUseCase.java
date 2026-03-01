package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.Contrato;
import reactor.core.publisher.Mono;

public interface RegistrarNumeroDeTituloUseCase {
    Mono<Contrato> registrar(String contratoId, String numeroDeTitulo);
}
