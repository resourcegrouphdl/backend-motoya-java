package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.dto.MovimientosResumenDto;
import com.motoyav2.cobranza.application.port.out.MovimientoPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MovimientoDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovimientosService {

    private final MovimientoPort movimientoPort;

    // -------------------------------------------------------------------------
    // Listar movimientos con resumen financiero
    // -------------------------------------------------------------------------

    public Mono<MovimientosResumenDto> listar(String contratoId) {
        return movimientoPort.findByContratoId(contratoId)
                .collectList()
                .map(this::buildResumen);
    }

    private MovimientosResumenDto buildResumen(List<MovimientoDocument> movimientos) {
        double totalCargos = movimientos.stream()
                .filter(m -> m.getMonto() != null && m.getMonto() > 0)
                .mapToDouble(MovimientoDocument::getMonto)
                .sum();

        double totalAbonos = movimientos.stream()
                .filter(m -> m.getMonto() != null && m.getMonto() < 0)
                .mapToDouble(m -> Math.abs(m.getMonto()))
                .sum();

        double saldoNeto = totalCargos - totalAbonos;

        return new MovimientosResumenDto(movimientos, totalCargos, totalAbonos, saldoNeto);
    }
}
