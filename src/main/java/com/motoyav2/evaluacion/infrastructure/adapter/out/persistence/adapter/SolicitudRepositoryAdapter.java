package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.application.port.out.SolicitudPort;
import com.motoyav2.evaluacion.domain.model.ExpedienteSeed;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper.SolicitudMaper;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.FirebaseSolicitudRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SolicitudRepositoryAdapter implements SolicitudPort {

    private final FirebaseSolicitudRepository firebaseSolicitudRepository;

    @Override
    public Mono<ExpedienteSeed> obtenerSolicitud(String formularioId) {
        return firebaseSolicitudRepository.findById(formularioId)
                .map(SolicitudMaper::toSeed);
    }
}
