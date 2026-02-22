package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.evaluacion.application.port.out.EvaluacionQueryPort;
import com.motoyav2.evaluacion.domain.model.EvaluacionResumen;
import com.motoyav2.evaluacion.domain.model.ResultadoPaginado;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.EvaluacionCreditoDocument;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.mapper.evaluacioncredito.EvaluacionResumenDocMapper;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.repository.evaluacioncredito.EvaluacionCreditoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class EvaluacionQueryAdapter implements EvaluacionQueryPort {

    private final EvaluacionCreditoRepository evaluacionCreditoRepository;

    @Override
    public Mono<ResultadoPaginado<EvaluacionResumen>> listarPaginado(int pagina, int porPagina) {
        int skip = (pagina - 1) * porPagina;

        Mono<Long> totalMono = evaluacionCreditoRepository.count();

        Mono<java.util.List<EvaluacionResumen>> itemsMono = evaluacionCreditoRepository.findAll()
                .map(EvaluacionResumenDocMapper::toDomain)
                .skip(skip)
                .take(porPagina)
                .collectList();

        return Mono.zip(itemsMono, totalMono)
                .map(tuple -> new ResultadoPaginado<>(tuple.getT1(), tuple.getT2(), pagina, porPagina));
    }

    @Override
    public Mono<EvaluacionCreditoDocument> buscarPorEvaluacionId(String evaluacionId) {
        return evaluacionCreditoRepository.findById(evaluacionId);
    }
}
