package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.application.port.out.VehiculoPort;
import com.motoyav2.evaluacion.domain.model.Vehiculo;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper.VehiculoFirebaseMapper;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.FirebaseVehiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class VehiculoRepositoryAdapter implements VehiculoPort {

    private final FirebaseVehiculoRepository firebaseVehiculoRepository;

    @Override
    public Mono<Vehiculo> buscarPorId(String vehiculoId) {
        return firebaseVehiculoRepository.findById(vehiculoId)
                .map(VehiculoFirebaseMapper::toDomain);
    }
}
