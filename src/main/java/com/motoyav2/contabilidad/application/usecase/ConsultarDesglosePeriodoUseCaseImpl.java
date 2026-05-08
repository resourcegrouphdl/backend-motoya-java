package com.motoyav2.contabilidad.application.usecase;

import com.motoyav2.contabilidad.domain.model.DesglosePeriodo;
import com.motoyav2.contabilidad.domain.model.MovimientoContable;
import com.motoyav2.contabilidad.domain.model.TipoMovimientoContable;
import com.motoyav2.contabilidad.domain.port.in.ConsultarDesglosePeriodoUseCase;
import com.motoyav2.contabilidad.domain.port.out.MovimientoContablePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultarDesglosePeriodoUseCaseImpl implements ConsultarDesglosePeriodoUseCase {

    private final MovimientoContablePort movimientoPort;

    @Override
    public Flux<DesglosePeriodo> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId) {
        return movimientoPort.findByPeriodo(desde, hasta, tiendaId)
                .collectList()
                .flatMapMany(movimientos -> {
                    Map<String, List<MovimientoContable>> porPeriodo = movimientos.stream()
                            .collect(Collectors.groupingBy(
                                    m -> m.getPeriodo() != null ? m.getPeriodo() : "",
                                    TreeMap::new,
                                    Collectors.toList()
                            ));

                    return Flux.fromIterable(porPeriodo.entrySet())
                            .map(entry -> {
                                String periodo = entry.getKey();
                                List<MovimientoContable> grupo = entry.getValue();

                                List<MovimientoContable> ingresos = grupo.stream()
                                        .filter(m -> m.getTipo() == TipoMovimientoContable.INGRESO_CUOTA)
                                        .toList();
                                List<MovimientoContable> costosTienda = grupo.stream()
                                        .filter(m -> m.getTipo() == TipoMovimientoContable.COSTO_TIENDA)
                                        .toList();
                                List<MovimientoContable> costosComision = grupo.stream()
                                        .filter(m -> m.getTipo() == TipoMovimientoContable.COSTO_COMISION)
                                        .toList();

                                double totalCobrado  = ingresos.stream().mapToDouble(MovimientoContable::getMontoTotal).sum();
                                double totalCapital  = ingresos.stream().mapToDouble(MovimientoContable::getMontoCapital).sum();
                                double totalInteres  = ingresos.stream().mapToDouble(MovimientoContable::getMontoInteres).sum();
                                double costoTienda   = costosTienda.stream().mapToDouble(MovimientoContable::getMontoCosto).sum();
                                double costoComision = costosComision.stream().mapToDouble(MovimientoContable::getMontoCosto).sum();
                                double utilidadBruta = totalInteres - costoTienda;
                                double utilidadNeta  = utilidadBruta - costoComision;

                                return DesglosePeriodo.builder()
                                        .periodo(periodo)
                                        .cantidadCobros(ingresos.size())
                                        .montoTotalCobrado(round2(totalCobrado))
                                        .montoCapital(round2(totalCapital))
                                        .montoInteres(round2(totalInteres))
                                        .costosTienda(round2(costoTienda))
                                        .costosComision(round2(costoComision))
                                        .utilidadBruta(round2(utilidadBruta))
                                        .utilidadNeta(round2(utilidadNeta))
                                        .build();
                            });
                })
                .onErrorResume(e -> {
                    log.error("[CONTABILIDAD] Error consultando desglose: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
