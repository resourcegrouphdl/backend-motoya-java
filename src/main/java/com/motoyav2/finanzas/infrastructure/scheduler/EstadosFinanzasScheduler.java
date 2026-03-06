package com.motoyav2.finanzas.infrastructure.scheduler;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.finanzas.application.port.out.KpisPort;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class EstadosFinanzasScheduler {

    private final Firestore db;
    private final KpisPort kpisPort;

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Lima")
    public void recalcularEstados() {
        log.info("[Scheduler Finanzas] Iniciando recálculo nocturno de estados");
        recalcularPagosFactura()
                .then(recalcularCuotas())
                .then(kpisPort.recalcularCompleto())
                .subscribe(
                        null,
                        e -> log.error("[Scheduler Finanzas] Error en recálculo: {}", e.getMessage()),
                        () -> log.info("[Scheduler Finanzas] Recálculo completado")
                );
    }

    // ── Pagos de facturas ─────────────────────────────────────────────────

    private Mono<Void> recalcularPagosFactura() {
        String hoy     = LocalDate.now().toString();
        String en3Dias = LocalDate.now().plusDays(3).toString();
        String ahora   = Instant.now().toString();

        Flux<?> marcarVencidos = toFlux(
                db.collectionGroup("pagos")
                        .whereEqualTo("estado", EstadoPago.PENDIENTE.name())
                        .whereLessThan("fechaProgramada", hoy).get())
                .flatMap(doc -> toMono(doc.getReference().update(
                        "estado", EstadoPago.VENCIDO.name(), "actualizadoEn", ahora)));

        Flux<?> marcarProximos = toFlux(
                db.collectionGroup("pagos")
                        .whereEqualTo("estado", EstadoPago.PENDIENTE.name())
                        .whereGreaterThanOrEqualTo("fechaProgramada", hoy)
                        .whereLessThanOrEqualTo("fechaProgramada", en3Dias).get())
                .flatMap(doc -> toMono(doc.getReference().update(
                        "estado", EstadoPago.PROXIMO_VENCER.name(), "actualizadoEn", ahora)));

        return Flux.merge(marcarVencidos, marcarProximos)
                .then()
                .doOnSuccess(v -> log.info("[Scheduler] Pagos de facturas actualizados"))
                .then(propagarEstadosFacturas());
    }

    private Mono<Void> propagarEstadosFacturas() {
        return toFlux(db.collection("facturas").whereNotEqualTo("estado", "PAGADO").get())
                .flatMap(facturaSnap -> {
                    String facturaId = facturaSnap.getId();
                    return toFlux(db.collection("facturas").document(facturaId).collection("pagos").get())
                            .collectList()
                            .flatMap(pagos -> {
                                long vencidos = pagos.stream().filter(p -> "VENCIDO".equals(p.getString("estado"))).count();
                                long proximos = pagos.stream().filter(p -> "PROXIMO_VENCER".equals(p.getString("estado"))).count();
                                long pagados  = pagos.stream().filter(p -> "PAGADO".equals(p.getString("estado"))).count();

                                String nuevoEstado;
                                if (pagados == pagos.size()) nuevoEstado = "PAGADO";
                                else if (vencidos > 0)       nuevoEstado = "VENCIDO";
                                else if (proximos > 0)       nuevoEstado = "PROXIMO_VENCER";
                                else                         nuevoEstado = "PENDIENTE";

                                return toMono(db.collection("facturas").document(facturaId).update(
                                        "estado", nuevoEstado,
                                        "_alertaActiva", !"PAGADO".equals(nuevoEstado),
                                        "_tieneVencidos", "VENCIDO".equals(nuevoEstado),
                                        "actualizadoEn", Instant.now().toString()
                                ));
                            });
                }).then();
    }

    // ── Cuotas de cuentas ─────────────────────────────────────────────────

    private Mono<Void> recalcularCuotas() {
        String hoy  = LocalDate.now().toString();
        String ahora = Instant.now().toString();

        Flux<?> marcarVencidas = toFlux(
                db.collectionGroup("cuotas")
                        .whereEqualTo("estado", "PENDIENTE")
                        .whereLessThan("fechaVencimiento", hoy).get())
                .flatMap(doc -> toMono(doc.getReference().update(
                        "estado", "VENCIDO", "actualizadoEn", ahora)));

        return marcarVencidas.then()
                .doOnSuccess(v -> log.info("[Scheduler] Cuotas de cuentas actualizadas"))
                .then(propagarEstadosCuentas());
    }

    private Mono<Void> propagarEstadosCuentas() {
        return toFlux(db.collection("cuentas_pagar").whereNotEqualTo("estado", "PAGADO").get())
                .flatMap(cuentaSnap -> {
                    String cuentaId = cuentaSnap.getId();
                    return toFlux(db.collection("cuentas_pagar").document(cuentaId).collection("cuotas").get())
                            .collectList()
                            .flatMap(cuotas -> {
                                long vencidas = cuotas.stream().filter(c -> "VENCIDO".equals(c.getString("estado"))).count();
                                long pagadas  = cuotas.stream().filter(c -> "PAGADO".equals(c.getString("estado"))).count();

                                String nuevoEstado;
                                if (pagadas == cuotas.size()) nuevoEstado = "PAGADO";
                                else if (vencidas > 0)        nuevoEstado = "VENCIDO";
                                else                          nuevoEstado = "PENDIENTE";

                                return toMono(db.collection("cuentas_pagar").document(cuentaId).update(
                                        "estado", nuevoEstado,
                                        "_alertaActiva", !"PAGADO".equals(nuevoEstado),
                                        "_tieneVencidos", "VENCIDO".equals(nuevoEstado),
                                        "actualizadoEn", Instant.now().toString()
                                ));
                            });
                }).then();
    }
}
