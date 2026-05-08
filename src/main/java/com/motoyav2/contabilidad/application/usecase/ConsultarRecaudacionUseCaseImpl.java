package com.motoyav2.contabilidad.application.usecase;

import com.motoyav2.contabilidad.domain.model.PuntoRecaudacion;
import com.motoyav2.contabilidad.domain.model.ResumenRecaudacion;
import com.motoyav2.contabilidad.domain.port.in.ConsultarRecaudacionUseCase;
import com.motoyav2.contabilidad.domain.port.out.MovimientoLedgerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultarRecaudacionUseCaseImpl implements ConsultarRecaudacionUseCase {

    private final MovimientoLedgerPort movimientoLedgerPort;

    @Override
    public Mono<ResumenRecaudacion> ejecutar(LocalDate desde, LocalDate hasta, String tiendaId, String agruparPor) {
        log.debug("Consultando recaudacion desde={} hasta={} tiendaId={} agruparPor={}", desde, hasta, tiendaId, agruparPor);

        String agrupacion = (agruparPor != null && !agruparPor.isBlank()) ? agruparPor.toUpperCase() : "MES";

        return movimientoLedgerPort.findPagosByPeriodo(desde, hasta, tiendaId)
                .collectList()
                .map(pagos -> {
                    // Agrupación por clave temporal
                    Map<String, Double> montosPorClave  = new LinkedHashMap<>();
                    Map<String, Integer> conteoPorClave = new LinkedHashMap<>();

                    for (var pago : pagos) {
                        if (pago.getFecha() == null) continue;
                        String clave = generarClave(pago.getFecha(), agrupacion);
                        montosPorClave.merge(clave, pago.getMonto() != null ? pago.getMonto() : 0.0, Double::sum);
                        conteoPorClave.merge(clave, 1, Integer::sum);
                    }

                    // Ordenar las claves cronológicamente
                    List<String> clavesOrdenadas = new ArrayList<>(montosPorClave.keySet());
                    Collections.sort(clavesOrdenadas);

                    List<PuntoRecaudacion> puntos = clavesOrdenadas.stream()
                            .map(clave -> PuntoRecaudacion.builder()
                                    .etiqueta(clave)
                                    .cantidadPagos(conteoPorClave.getOrDefault(clave, 0))
                                    .montoTotal(montosPorClave.getOrDefault(clave, 0.0))
                                    .build())
                            .toList();

                    double montoTotal = pagos.stream()
                            .mapToDouble(p -> p.getMonto() != null ? p.getMonto() : 0.0)
                            .sum();
                    int totalPagos = pagos.size();
                    double promedio = totalPagos > 0 ? montoTotal / totalPagos : 0.0;

                    return ResumenRecaudacion.builder()
                            .desde(desde)
                            .hasta(hasta)
                            .totalPagos(totalPagos)
                            .montoTotal(montoTotal)
                            .promedioTicket(promedio)
                            .puntos(puntos)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error calculando recaudacion: {}", e.getMessage(), e);
                    return Mono.just(ResumenRecaudacion.builder()
                            .desde(desde).hasta(hasta)
                            .totalPagos(0).montoTotal(0.0).promedioTicket(0.0)
                            .puntos(List.of())
                            .build());
                });
    }

    private String generarClave(LocalDate fecha, String agrupacion) {
        return switch (agrupacion) {
            case "DIA" -> fecha.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "SEMANA" -> {
                WeekFields wf = WeekFields.ISO;
                yield fecha.getYear() + "-S" + String.format("%02d", fecha.get(wf.weekOfWeekBasedYear()));
            }
            default -> // MES
                fecha.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        };
    }
}
