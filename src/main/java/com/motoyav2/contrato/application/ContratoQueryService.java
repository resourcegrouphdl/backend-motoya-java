package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.model.ContratoListItem;
import com.motoyav2.contrato.domain.port.in.ListarContratosUseCase;
import com.motoyav2.contrato.domain.port.in.ObtenerContratoUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ContratoQueryService implements ListarContratosUseCase, ObtenerContratoUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Flux<ContratoListItem> listar() {
        return contratoRepository.findAll();
    }

  @Override
  public Flux<ContratoListItem> listarrContratosPorTienda(String tiendaId) {
    return contratoRepository.findByTiendaId(tiendaId);
  }

  @Override
    public Mono<Contrato> obtenerPorId(String id) {
        return contratoRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + id)));
    }
}
