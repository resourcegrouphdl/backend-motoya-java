package com.motoyav2.riesgointerno.application.usecase;

import com.motoyav2.riesgointerno.domain.port.in.ListarRegistrosUseCase;
import com.motoyav2.riesgointerno.domain.port.out.RegistroRiesgoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListarRegistrosUseCaseImpl implements ListarRegistrosUseCase {

    private final RegistroRiesgoRepository repository;

    @Override
    public Mono<PagedResult> listar(String nivelRiesgo, String estadoRegistro, String search, int page, int size) {
        int offset = page * size;
        log.info("[ListarRiesgo] page={} size={} offset={} nivel={} estado={} search={}", page, size, offset, nivelRiesgo, estadoRegistro, search);
        return Mono.zip(
                repository.findAll(nivelRiesgo, estadoRegistro, search, size, offset).collectList()
                        .doOnSuccess(items -> log.info("[ListarRiesgo] findAll devolvió {} items", items.size()))
                        .doOnError(e  -> log.error("[ListarRiesgo] findAll ERROR: {}", e.getMessage(), e)),
                repository.count(nivelRiesgo, estadoRegistro)
                        .doOnSuccess(c -> log.info("[ListarRiesgo] count={}", c))
                        .doOnError(e -> log.error("[ListarRiesgo] count ERROR: {}", e.getMessage(), e))
        ).map(t -> new PagedResult(t.getT1(), t.getT2(), page, size))
         .doOnError(e -> log.error("[ListarRiesgo] zip ERROR: {}", e.getMessage(), e));
    }
}
