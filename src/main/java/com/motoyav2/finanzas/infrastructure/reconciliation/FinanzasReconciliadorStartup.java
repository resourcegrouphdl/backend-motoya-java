package com.motoyav2.finanzas.infrastructure.reconciliation;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.contrato.domain.port.out.FinanzasIntegrationPort;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.document.ContratoDocument;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.mapper.ContratoDocumentMapper;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Reconciliador de arranque para el módulo Finanzas.
 *
 * Espera 5 segundos al iniciar para que la conexión gRPC/TLS de Firestore
 * esté estable en Cloud Run, luego reintenta hasta 3 veces con backoff.
 *
 * Solo ejecuta el barrido real la PRIMERA vez (flag en /finanzas_config/reconciliacion).
 * Arranques posteriores hacen 1 sola lectura y salen.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanzasReconciliadorStartup implements ApplicationRunner {

    private static final String COL_CONFIG  = "finanzas_config";
    private static final String DOC_FLAG    = "reconciliacion";
    private static final String CAMPO_OK    = "completado";
    private static final String CAMPO_FECHA = "fechaEjecucion";
    private static final String CAMPO_TOTAL = "totalProcesados";

    private final Firestore db;
    private final FinanzasIntegrationPort finanzasIntegrationPort;

    @Override
    public void run(ApplicationArguments args) {
        // Delay inicial: esperar que la conexión TLS/gRPC de Firestore esté lista en Cloud Run
        Mono.delay(Duration.ofSeconds(5))
                .then(verificarYEjecutar())
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(5))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(signal -> log.warn(
                                "[Reconciliador Finanzas] Reintentando ({}/{}) — causa: {}",
                                signal.totalRetries() + 1, 3, signal.failure().getMessage())))
                .subscribe(
                        null,
                        e -> log.error("[Reconciliador Finanzas] Falló tras reintentos — se ejecutará en el próximo arranque: {}", e.getMessage())
                );
    }

    private Mono<Void> verificarYEjecutar() {
        return FirestoreReactiveUtils.toMono(
                db.collection(COL_CONFIG).document(DOC_FLAG).get())
                .flatMap(snap -> {
                    if (snap.exists() && Boolean.TRUE.equals(snap.getBoolean(CAMPO_OK))) {
                        log.info("[Reconciliador Finanzas] Ya ejecutado anteriormente — omitiendo barrido");
                        return Mono.empty();
                    }
                    log.info("[Reconciliador Finanzas] Primera ejecución — iniciando barrido...");
                    return ejecutarBarrido();
                });
    }

    private Mono<Void> ejecutarBarrido() {
        return FirestoreReactiveUtils.toFlux(
                db.collection("contratos")
                        .whereIn("estado", List.of("FIRMADO", "ACTIVO", "COMPLETADO"))
                        .get())
                .map(snap -> snap.toObject(ContratoDocument.class))
                .flatMap(doc -> {
                    String contratoId = doc.getId();
                    return FirestoreReactiveUtils.toMono(
                            db.collection("facturas").document(contratoId).get())
                            .flatMap(facturaSnap -> {
                                if (facturaSnap.exists()) {
                                    log.debug("[Reconciliador] Factura ya existe — contratoId={}", contratoId);
                                    return Mono.just(0);
                                }
                                return Mono.fromCallable(() -> ContratoDocumentMapper.toDomain(doc))
                                        .flatMap(contrato -> finanzasIntegrationPort
                                                .iniciarFacturaDesdeContrato(contrato)
                                                .doOnSuccess(v -> log.info(
                                                        "[Reconciliador] Factura creada — contratoId={}", contratoId))
                                                .onErrorResume(e -> {
                                                    log.error("[Reconciliador] Error en contratoId={}: {}",
                                                            contratoId, e.getMessage());
                                                    return Mono.empty();
                                                })
                                                .thenReturn(1));
                            });
                }, 5)
                .reduce(0, Integer::sum)
                .flatMap(this::marcarComoCompletado)
                .then();
    }

    private Mono<Void> marcarComoCompletado(int totalCreadas) {
        log.info("[Reconciliador Finanzas] Barrido completado — facturas nuevas creadas: {}", totalCreadas);
        return FirestoreReactiveUtils.toMono(
                db.collection(COL_CONFIG).document(DOC_FLAG).set(Map.of(
                        CAMPO_OK,    true,
                        CAMPO_FECHA, Instant.now().toString(),
                        CAMPO_TOTAL, totalCreadas
                ))
        ).then();
    }
}
