package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.DocumentoGenerado;
import reactor.core.publisher.Flux;

public interface GenerarContratoPdfUseCase {

  Flux<DocumentoGenerado> documentosGenerados(String id);

}
