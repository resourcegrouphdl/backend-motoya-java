package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.application.port.out.TiendaPort;
import com.motoyav2.evaluacion.domain.model.TiendaInfo;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.TiendaProfileFirebaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TiendaProfileAdapter implements TiendaPort {

    private final TiendaProfileFirebaseRepository tiendaProfileFirebaseRepository;

    @Override
    public Mono<TiendaInfo> obtenerInfoTienda(String tiendaId) {
        return tiendaProfileFirebaseRepository.findById(tiendaId)
                .map(doc -> new TiendaInfo(
                        doc.getBusinessName(),
                        doc.getTaxId()
                ));
    }
}
