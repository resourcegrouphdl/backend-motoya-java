package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.EvidenciaDocumento;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SubirDocumentoPostFirmaUseCase {

    /**
     * @param tipo "TIVE" | "SOAT" | "PLACA_RODAJE" | "ACTA_ENTREGA"
     * Para ACTA_ENTREGA la lista puede tener múltiples elementos (se agregan al acumulado).
     * Para el resto se usa únicamente el primer elemento.
     */
    Mono<Contrato> subir(String contratoId, String tipo, List<EvidenciaDocumento> evidencias);
}
