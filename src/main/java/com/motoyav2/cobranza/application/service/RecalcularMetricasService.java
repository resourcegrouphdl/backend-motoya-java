package com.motoyav2.cobranza.application.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.motoyav2.cobranza.application.port.out.CasoCobranzaPort;
import com.motoyav2.cobranza.application.port.out.MetricasPort;
import com.motoyav2.cobranza.application.port.out.VoucherPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.CasoCobranzaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.MetricasDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Recalcula los KPIs de cobranza en tiempo real leyendo las colecciones de Firestore
 * y persiste el resultado como singleton "resumen_actual" en cobranzas-metricas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecalcularMetricasService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String RESUMEN_ID = "resumen_actual";

    private final CasoCobranzaPort casoPort;
    private final VoucherPort       voucherPort;
    private final MetricasPort      metricasPort;
    private final Firestore         firestore;

    public Mono<MetricasDocument> recalcular() {
        log.info("[METRICAS] Iniciando recálculo de KPIs de cobranza");

        return casoPort.findAll()
                .collectList()
                .flatMap(casos -> {
                    int casosActivos  = casos.size();
                    int casosCriticos = (int) casos.stream()
                            .filter(c -> "MORA_CRITICA".equals(c.getNivelEstrategia())
                                      || "JUDICIAL".equals(c.getNivelEstrategia()))
                            .count();
                    double moraTotal = casos.stream()
                            .mapToDouble(c -> c.getSaldoActual() != null ? c.getSaldoActual() : 0.0)
                            .sum();

                    int vouchersPendientes  = contarVouchersPendientes();
                    int promesasVencenHoy   = contarPromesasPorEstadoYFecha("VIGENTE", true);
                    int promesasIncumplidas = contarPromesasPorEstadoYFecha("INCUMPLIDA", false);

                    MetricasDocument doc = MetricasDocument.builder()
                            .id(RESUMEN_ID)
                            .casosActivos(casosActivos)
                            .casosCriticos(casosCriticos)
                            .moraTotal(moraTotal)
                            .vouchersPendientes(vouchersPendientes)
                            .promesasVencenHoy(promesasVencenHoy)
                            .promesasIncumplidas(promesasIncumplidas)
                            .recuperacionMes(calcularRecuperacionMes(casos))
                            .porcentajeAutomatizado(0.0)
                            .tasaRecuperacion(moraTotal > 0
                                    ? calcularRecuperacionMes(casos) / moraTotal * 100 : 0.0)
                            .ultimaActualizacion(new Date())
                            .build();

                    return metricasPort.save(doc)
                            .doOnSuccess(saved -> log.info(
                                    "[METRICAS] KPIs actualizados — activos={} criticos={} mora={}",
                                    casosActivos, casosCriticos, moraTotal));
                })
                .onErrorResume(ex -> {
                    log.error("[METRICAS] Error al recalcular KPIs: {}", ex.getMessage());
                    return metricasPort.findResumenActual()
                            .switchIfEmpty(Mono.just(emptyDoc()));
                });
    }

    // ── Métodos síncronos con Firestore SDK (collectionGroup no está en Spring Data) ──

    private int contarVouchersPendientes() {
        try {
            return (int) firestore.collection("cobranzas-vouchers")
                    .whereEqualTo("estado", "PENDIENTE")
                    .get().get()
                    .getDocuments().stream().count();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.warn("[METRICAS] No se pudo contar vouchers pendientes: {}", e.getMessage());
            return 0;
        }
    }

    private int contarPromesasPorEstadoYFecha(String estado, boolean soloHoy) {
        try {
            String hoy = LocalDate.now(LIMA).toString();
            var query = firestore.collectionGroup("promesas")
                    .whereEqualTo("estado", estado);
            if (soloHoy) {
                query = query.whereEqualTo("fechaCompromiso", hoy);
            }
            return query.get().get().getDocuments().size();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.warn("[METRICAS] No se pudo contar promesas {}: {}", estado, e.getMessage());
            return 0;
        }
    }

    private double calcularRecuperacionMes(List<CasoCobranzaDocument> casos) {
        // Suma totalPagado si está disponible en el documento del caso
        return casos.stream()
                .mapToDouble(c -> c.getTotalPagado() != null ? c.getTotalPagado() : 0.0)
                .sum();
    }

    private MetricasDocument emptyDoc() {
        return MetricasDocument.builder()
                .id(RESUMEN_ID)
                .casosActivos(0).casosCriticos(0)
                .moraTotal(0.0).recuperacionMes(0.0)
                .vouchersPendientes(0).promesasVencenHoy(0)
                .promesasIncumplidas(0).porcentajeAutomatizado(0.0)
                .tasaRecuperacion(0.0)
                .ultimaActualizacion(new Date())
                .build();
    }
}
