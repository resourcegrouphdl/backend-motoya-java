package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.application.port.out.ReferenciasPort;
import com.motoyav2.evaluacion.domain.model.ReferenciasDelTitular;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper.ReferenciaFirebaseMapper;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.FirebaseReferenciasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class ReferenciasRepositoryAdapter implements ReferenciasPort {

    private final FirebaseReferenciasRepository firebaseReferenciasRepository;

    @Override
    public Flux<ReferenciasDelTitular> buscarPorIds(List<String> referenciasIds) {
        if (referenciasIds == null || referenciasIds.isEmpty()) {
            return Flux.empty();
        }
        AtomicInteger counter = new AtomicInteger(1);
        return Flux.fromIterable(referenciasIds)
                .flatMapSequential(id -> firebaseReferenciasRepository.findById(id))
                .map(doc -> ReferenciaFirebaseMapper.toDomain(doc, counter.getAndIncrement()));
    }
}
