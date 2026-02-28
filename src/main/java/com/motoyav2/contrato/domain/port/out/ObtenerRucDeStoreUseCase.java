package com.motoyav2.contrato.domain.port.out;

import reactor.core.publisher.Mono;

public interface ObtenerRucDeStoreUseCase {

  Mono<String> obtenerRucDeStore(String idTienda);

}
