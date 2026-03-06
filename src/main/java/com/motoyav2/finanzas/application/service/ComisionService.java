package com.motoyav2.finanzas.application.service;

import com.motoyav2.finanzas.application.port.in.ListarComisionesUseCase;
import com.motoyav2.finanzas.application.port.in.PagarComisionUseCase;
import com.motoyav2.finanzas.application.port.out.ComisionPort;
import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComisionService implements ListarComisionesUseCase, PagarComisionUseCase {

    private final ComisionPort comisionPort;

    @Override
    public Flux<ComisionVendedor> ejecutar(String tiendaId, LocalDate fechaInicio, LocalDate fechaFin) {
        return comisionPort.findAll(tiendaId, fechaInicio, fechaFin);
    }

    @Override
    public Mono<Void> ejecutar(String comisionId) {
        return comisionPort.marcarPagada(comisionId);
    }
}
