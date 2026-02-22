package com.motoyav2.evaluacion.application.port.in;

import com.motoyav2.evaluacion.domain.model.EvaluacionResumen;
import com.motoyav2.evaluacion.domain.model.ResultadoPaginado;
import reactor.core.publisher.Mono;

public interface ListarEvaluacionesUseCase {

    Mono<ResultadoPaginado<EvaluacionResumen>> ejecutar(int pagina, int porPagina);
}
