package com.motoyav2.evaluacion.application.port.out;

import com.motoyav2.evaluacion.domain.model.EvaluacionResumen;
import com.motoyav2.evaluacion.domain.model.ResultadoPaginado;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.EvaluacionCreditoDocument;
import reactor.core.publisher.Mono;

public interface EvaluacionQueryPort {

    Mono<ResultadoPaginado<EvaluacionResumen>> listarPaginado(int pagina, int porPagina);

    Mono<EvaluacionCreditoDocument> buscarPorEvaluacionId(String evaluacionId);
}
