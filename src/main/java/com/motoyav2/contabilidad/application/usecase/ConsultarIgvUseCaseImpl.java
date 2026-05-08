package com.motoyav2.contabilidad.application.usecase;

import com.motoyav2.contabilidad.domain.model.ResumenIgv;
import com.motoyav2.contabilidad.domain.port.in.ConsultarIgvUseCase;
import com.motoyav2.contabilidad.domain.port.out.ComprobanteLedgerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultarIgvUseCaseImpl implements ConsultarIgvUseCase {

    private final ComprobanteLedgerPort comprobanteLedgerPort;

    @Override
    public Mono<ResumenIgv> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId) {
        log.debug("Consultando IGV desde={} hasta={} tiendaId={}", desde, hasta, tiendaId);

        return comprobanteLedgerPort.findByPeriodo(desde, hasta, tiendaId, null)
                .collectList()
                .map(comprobantes -> {
                    double totalSubTotal = 0.0;
                    double totalIgv      = 0.0;
                    double totalBruto    = 0.0;
                    int cantBoletas      = 0;
                    int cantFacturas     = 0;
                    int cantAnulados     = 0;

                    for (var c : comprobantes) {
                        boolean anulado = "ANULADO".equalsIgnoreCase(c.getEstado());
                        if (anulado) {
                            cantAnulados++;
                            continue;
                        }
                        totalSubTotal += c.getSubTotal() != null ? c.getSubTotal() : 0.0;
                        totalIgv      += c.getIgv()      != null ? c.getIgv()      : 0.0;
                        totalBruto    += c.getTotal()    != null ? c.getTotal()     : 0.0;

                        if ("BOLETA".equalsIgnoreCase(c.getTipo()))   cantBoletas++;
                        if ("FACTURA".equalsIgnoreCase(c.getTipo()))  cantFacturas++;
                    }

                    return ResumenIgv.builder()
                            .desde(desde)
                            .hasta(hasta)
                            .totalSubTotal(totalSubTotal)
                            .totalIgv(totalIgv)
                            .totalBruto(totalBruto)
                            .cantidadBoletas(cantBoletas)
                            .cantidadFacturas(cantFacturas)
                            .cantidadAnulados(cantAnulados)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error calculando resumen IGV: {}", e.getMessage(), e);
                    return Mono.just(ResumenIgv.builder()
                            .desde(desde).hasta(hasta)
                            .totalSubTotal(0.0).totalIgv(0.0).totalBruto(0.0)
                            .cantidadBoletas(0).cantidadFacturas(0).cantidadAnulados(0)
                            .build());
                });
    }
}
