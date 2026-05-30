package com.motoyav2.riesgointerno.domain.port.in;

import com.motoyav2.riesgointerno.domain.model.RegistroRiesgo;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ListarRegistrosUseCase {

    Mono<PagedResult> listar(String nivelRiesgo, String estadoRegistro, String search, int page, int size);

    record PagedResult(List<RegistroRiesgo> items, long total, int page, int size) {}
}
