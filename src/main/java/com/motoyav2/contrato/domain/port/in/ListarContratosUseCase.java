package com.motoyav2.contrato.domain.port.in;

import com.motoyav2.contrato.domain.model.ContratoListItem;
import reactor.core.publisher.Flux;

public interface ListarContratosUseCase {

    Flux<ContratoListItem> listar();

    Flux<ContratoListItem> listarrContratosPorTienda(String tiendaId);

}
