package com.motoyav2.riesgointerno.domain.port.in;

import com.motoyav2.riesgointerno.domain.enums.EstadoRegistro;
import com.motoyav2.riesgointerno.domain.enums.NivelRiesgo;
import com.motoyav2.riesgointerno.domain.enums.TipoRiesgo;
import com.motoyav2.riesgointerno.domain.enums.TipoSujeto;
import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;
import reactor.core.publisher.Mono;

import java.util.List;

public interface RegistrarRiesgoUseCase {

    Mono<RegistroRiesgo> registrar(Command command);

    record Command(
            String dniRegistrado,
            String nombreRegistrado,
            List<String> telefonos,
            TipoSujeto tipoSujeto,
            NivelRiesgo nivelRiesgo,
            EstadoRegistro estadoRegistro,
            TipoRiesgo tipoRiesgo,
            String contratoIdRelacionado,
            String solicitudIdRelacionado,
            Double montoDeudaPendiente,
            String fechaIncidente,
            String descripcion,
            List<String> evidencias,
            List<String> condicionesRehabilitacion,
            String registradoPor
    ) {}
}
