package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.application.port.out.ClientePort;
import com.motoyav2.evaluacion.domain.model.Persona;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.formMapper.ClienteMapper;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.formulario.FirebaseClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ClienteRepositoryAdapter implements ClientePort {

    private final FirebaseClienteRepository firebaseClienteRepository;

    @Override
    public Mono<Persona> buscarPorId(String clienteId) {
        return firebaseClienteRepository.findById(clienteId)
                .map(ClienteMapper::toDomain);
    }
}
