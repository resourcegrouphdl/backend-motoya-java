package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.model.Contrato;
import reactor.core.publisher.Mono;

public interface ValidarEvidenciaDocumentoUseCase {

    /**
     * @param tipo "TIVE" | "SOAT" | "PLACA_RODAJE"
     */
    Mono<Contrato> validar(String contratoId, String tipo, EstadoValidacion estado,
                           String observacion, String validadoPor);
}
