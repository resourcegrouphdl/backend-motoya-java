package com.motoyav2.contrato.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.contrato.domain.port.out.ObtenerRucDeStoreUseCase;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.document.TiendaDetalles;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.repository.TiendaDetallesFirestore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TiendaDetalleAdapter implements ObtenerRucDeStoreUseCase {


  private final TiendaDetallesFirestore tiendaDetallesFirestore;

  @Override
  public  Mono<String> obtenerRucDeStore(String idTienda) {
    return tiendaDetallesFirestore.findById(idTienda)
        .map(TiendaDetalles::getTaxId);
  }
}
