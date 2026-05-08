package com.motoyav2.contabilidad.application.usecase;

import com.motoyav2.contabilidad.domain.model.TipoMovimientoContable;
import com.motoyav2.contabilidad.domain.model.UtilidadPeriodo;
import com.motoyav2.contabilidad.domain.port.in.ConsultarUtilidadUseCase;
import com.motoyav2.contabilidad.domain.port.out.MovimientoContablePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultarUtilidadUseCaseImpl implements ConsultarUtilidadUseCase {

    private final MovimientoContablePort movimientoPort;

    @Override
    public Mono<UtilidadPeriodo> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId) {
        return movimientoPort.findByPeriodo(desde, hasta, tiendaId)
                .collectList()
                .map(movimientos -> {
                    double totalIngresos   = 0, totalCapital = 0, totalInteres = 0;
                    double costosTienda    = 0, costosComision = 0;
                    int cobros = 0, pagosTienda = 0, comisiones = 0;

                    for (var m : movimientos) {
                        if (m.getTipo() == TipoMovimientoContable.INGRESO_CUOTA) {
                            totalIngresos += m.getMontoTotal();
                            totalCapital  += m.getMontoCapital();
                            totalInteres  += m.getMontoInteres();
                            cobros++;
                        } else if (m.getTipo() == TipoMovimientoContable.COSTO_TIENDA) {
                            costosTienda += m.getMontoCosto();
                            pagosTienda++;
                        } else if (m.getTipo() == TipoMovimientoContable.COSTO_COMISION) {
                            costosComision += m.getMontoCosto();
                            comisiones++;
                        }
                    }

                    double utilidadBruta = totalInteres - costosTienda;
                    double utilidadNeta  = utilidadBruta - costosComision;
                    double margenNeto    = totalIngresos > 0
                            ? (utilidadNeta / totalIngresos) * 100.0 : 0.0;

                    return UtilidadPeriodo.builder()
                            .desde(desde.toString())
                            .hasta(hasta.toString())
                            .tiendaId(tiendaId)
                            .totalIngresos(round2(totalIngresos))
                            .totalCapitalRecuperado(round2(totalCapital))
                            .totalInteresGanado(round2(totalInteres))
                            .totalCostosTienda(round2(costosTienda))
                            .totalCostosComision(round2(costosComision))
                            .utilidadBruta(round2(utilidadBruta))
                            .utilidadNeta(round2(utilidadNeta))
                            .margenNeto(round2(margenNeto))
                            .cantidadCobros(cobros)
                            .cantidadPagosTienda(pagosTienda)
                            .cantidadComisionesPagadas(comisiones)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("[CONTABILIDAD] Error calculando utilidad: {}", e.getMessage());
                    return Mono.just(utilidadVacia(desde, hasta, tiendaId));
                });
    }

    private UtilidadPeriodo utilidadVacia(LocalDate desde, LocalDate hasta, String tiendaId) {
        return UtilidadPeriodo.builder()
                .desde(desde.toString()).hasta(hasta.toString()).tiendaId(tiendaId)
                .totalIngresos(0).totalCapitalRecuperado(0).totalInteresGanado(0)
                .totalCostosTienda(0).totalCostosComision(0)
                .utilidadBruta(0).utilidadNeta(0).margenNeto(0)
                .cantidadCobros(0).cantidadPagosTienda(0).cantidadComisionesPagadas(0)
                .build();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
