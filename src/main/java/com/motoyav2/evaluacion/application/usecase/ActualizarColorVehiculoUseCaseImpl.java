package com.motoyav2.evaluacion.application.usecase;

import com.google.cloud.Timestamp;
import com.motoyav2.evaluacion.domain.port.in.ActualizarColorVehiculoUseCase;
import com.motoyav2.evaluacion.domain.port.out.VehiculoRepository;
import com.motoyav2.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ActualizarColorVehiculoUseCaseImpl implements ActualizarColorVehiculoUseCase {

    private final VehiculoRepository vehiculoRepository;

    @Override
    public Mono<Void> actualizarColor(String vehiculoId, String color) {
        if (color == null || color.isBlank()) {
            return Mono.error(new BadRequestException("El color no puede estar vacío"));
        }
        return vehiculoRepository.updateFields(vehiculoId, Map.of(
                "color",     color.trim(),
                "updatedAt", Timestamp.now()
        ));
    }
}
