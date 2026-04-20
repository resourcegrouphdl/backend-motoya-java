package com.motoyav2.evaluacion.application.usecase;

import com.motoyav2.evaluacion.domain.port.in.EliminarSolicitudUseCase;
import com.motoyav2.evaluacion.domain.port.out.SolicitudRepository;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class EliminarSolicitudUseCaseImpl implements EliminarSolicitudUseCase {

    private final SolicitudRepository solicitudRepository;

    @Override
    public Mono<Void> eliminar(String solicitudId) {
        return solicitudRepository.findById(solicitudId)
                .switchIfEmpty(Mono.error(new NotFoundException("Solicitud no encontrada: " + solicitudId)))
                .flatMap(solicitud -> solicitudRepository.delete(solicitudId))
                .doOnSuccess(v -> log.info("[ELIMINAR] Solicitud eliminada: {}", solicitudId));
    }
}
