package com.motoyav2.contrato.application;

import com.motoyav2.contrato.domain.port.in.EliminarContratoUseCase;
import com.motoyav2.contrato.domain.port.out.ContratoRepository;
import com.motoyav2.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class EliminarContratoService implements EliminarContratoUseCase {

    private final ContratoRepository contratoRepository;

    @Override
    public Mono<Void> eliminar(String contratoId) {
        return contratoRepository.findById(contratoId)
                .switchIfEmpty(Mono.error(new NotFoundException("Contrato no encontrado: " + contratoId)))
                .flatMap(contrato -> {
                    log.info("Eliminando contrato id={} numero={}", contrato.id(), contrato.numeroContrato());
                    return contratoRepository.deleteById(contratoId);
                });
    }
}
