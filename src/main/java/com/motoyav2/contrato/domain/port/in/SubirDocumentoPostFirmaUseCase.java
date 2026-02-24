package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.EvidenciaDocumento;
import reactor.core.publisher.Mono;

public interface SubirDocumentoPostFirmaUseCase {

    /**
     * @param tipo "TIVE" | "SOAT" | "PLACA_RODAJE"
     */
    Mono<Contrato> subir(String contratoId, String tiendaId, String tipo, EvidenciaDocumento evidencia);
}
