package com.motoyav2.evaluacion.application.usecase;

import com.motoyav2.evaluacion.domain.model.HistorialEstado;
import com.motoyav2.evaluacion.domain.port.in.ObtenerHistorialUseCase;
import com.motoyav2.evaluacion.domain.port.out.HistorialEstadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ObtenerHistorialUseCaseImpl implements ObtenerHistorialUseCase {

    private final HistorialEstadoRepository historialEstadoRepository;

    @Override
    public Flux<HistorialEstado> ejecutar(String solicitudId) {
        return historialEstadoRepository.findBySolicitudId(solicitudId);
    }
}
