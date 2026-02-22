package com.motoyav2.evaluacion.application.service;

import com.motoyav2.evaluacion.application.port.in.ListarEvaluacionesUseCase;
import com.motoyav2.evaluacion.application.port.out.EvaluacionQueryPort;
import com.motoyav2.evaluacion.domain.model.EvaluacionResumen;
import com.motoyav2.evaluacion.domain.model.ResultadoPaginado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListarEvaluacionesService implements ListarEvaluacionesUseCase {

    private final EvaluacionQueryPort evaluacionQueryPort;

    @Override
    public Mono<ResultadoPaginado<EvaluacionResumen>> ejecutar(int pagina, int porPagina) {
        log.info("Listando evaluaciones - página: {}, porPágina: {}", pagina, porPagina);
        return evaluacionQueryPort.listarPaginado(pagina, porPagina);
    }
}
