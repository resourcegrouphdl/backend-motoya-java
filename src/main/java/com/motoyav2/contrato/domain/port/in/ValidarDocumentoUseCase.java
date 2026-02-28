package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.enums.EstadoValidacion;
import com.motoyav2.contrato.domain.model.Contrato;
import reactor.core.publisher.Mono;

public interface ValidarDocumentoUseCase {

    Mono<Contrato> validar(String contratoId, String tipoDocumento, EstadoValidacion estado, String observacion, String validadoPor, String boucherId);
}
