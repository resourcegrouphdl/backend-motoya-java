package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.domain.port.out.TiendaNombrePort;
import com.motoyav2.gestion.infrastructure.adapter.out.persistence.repository.TiendaProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TiendaNombreAdapter implements TiendaNombrePort {

    private final TiendaProfileRepository tiendaProfileRepository;

    @Override
    public Mono<String> resolverNombre(String tiendaId) {
        if (tiendaId == null || tiendaId.isBlank()) return Mono.just("");
        return tiendaProfileRepository.findById(tiendaId)
                .map(doc -> {
                    String name = doc.getBusinessName();
                    return (name != null && !name.isBlank()) ? name : tiendaId;
                })
                .defaultIfEmpty(tiendaId);
    }
}
