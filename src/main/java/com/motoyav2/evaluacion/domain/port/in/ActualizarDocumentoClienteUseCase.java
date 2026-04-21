package com.motoyav2.evaluacion.domain.port.in;

import reactor.core.publisher.Mono;

public interface ActualizarDocumentoClienteUseCase {
    Mono<Void> actualizarDocumento(String clienteId, String documentType, String documentNumber);
}
