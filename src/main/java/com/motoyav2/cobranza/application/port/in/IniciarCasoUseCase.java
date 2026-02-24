package com.motoyav2.cobranza.application.port.in;

import com.motoyav2.cobranza.application.port.in.command.IniciarCasoCommand;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import reactor.core.publisher.Mono;

public interface IniciarCasoUseCase {
    Mono<CasoCobranzaDocument> ejecutar(IniciarCasoCommand command);
}
