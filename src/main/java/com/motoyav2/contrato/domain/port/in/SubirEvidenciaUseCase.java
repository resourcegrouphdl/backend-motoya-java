package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.EvidenciaFirma;
import reactor.core.publisher.Mono;

public interface SubirEvidenciaUseCase {
    Mono<EvidenciaFirma> subir(String contratoId, EvidenciaFirma evidencia);
}
