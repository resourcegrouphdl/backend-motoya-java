package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.port.in.ActualizarTelefonoClienteUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ActualizarTelefonoClienteUseCaseImpl implements ActualizarTelefonoClienteUseCase {

    private final ClienteRepository clienteRepository;

    @Override
    public Mono<Void> actualizarTelefono(String clienteId, String telefono1) {
        if (telefono1 == null || telefono1.isBlank()) {
            return Mono.error(new BadRequestException("El teléfono no puede estar vacío"));
        }
        return clienteRepository.updateFields(clienteId, Map.of(
                "telefono1", telefono1.trim(),
                "updatedAt", Timestamp.now()
        ));
    }
}
