package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.port.in.ActualizarDireccionClienteUseCase;
import com.motoyav2.evaluacion.domain.port.out.ClienteRepository;
import com.motoyav2.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ActualizarDireccionClienteUseCaseImpl implements ActualizarDireccionClienteUseCase {

    private final ClienteRepository clienteRepository;

    @Override
    public Mono<Void> actualizarDireccion(String clienteId, String direccion,
                                           String distrito, String provincia, String departamento) {
        if (direccion == null || direccion.isBlank()) {
            return Mono.error(new BadRequestException("La dirección no puede estar vacía"));
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("direccion",    direccion.trim());
        if (distrito    != null && !distrito.isBlank())    fields.put("distrito",    distrito.trim());
        if (provincia   != null && !provincia.isBlank())   fields.put("provincia",   provincia.trim());
        if (departamento != null && !departamento.isBlank()) fields.put("departamento", departamento.trim());
        fields.put("updatedAt", Timestamp.now());

        return clienteRepository.updateFields(clienteId, fields);
    }
}
