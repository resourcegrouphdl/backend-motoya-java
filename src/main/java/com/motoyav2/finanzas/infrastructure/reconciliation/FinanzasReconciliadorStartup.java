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

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Reconciliador de arranque para el módulo Finanzas.
 *
 * Se ejecuta en cada cold start de Cloud Run, pero usa un flag en Firestore
 * para no repetir el trabajo: solo hace el barrido real la PRIMERA vez.
 *
 * Flujo:
 *   1. Leer /finanzas_config/reconciliacion
 *   2. Si existe y completado=true → salir (1 lectura, costo mínimo)
 *   3. Si no existe → barrer contratos FIRMADO/ACTIVO/COMPLETADO
 *      → crear facturas faltantes
 *      → escribir flag completado=true
 *
 * Es idempotente: el adapter verifica existencia antes de escribir en /facturas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanzasReconciliadorStartup implements ApplicationRunner {

    private static final String COL_CONFIG   = "finanzas_config";
    private static final String DOC_FLAG     = "reconciliacion";
    private static final String CAMPO_OK     = "completado";
    private static final String CAMPO_FECHA  = "fechaEjecucion";
    private static final String CAMPO_TOTAL  = "totalProcesados";

    private final Firestore db;
    private final FinanzasIntegrationPort finanzasIntegrationPort;

    @Override
    public void run(ApplicationArguments args) {
        verificarYEjecutar()
                .subscribe(
                        null,
                        e -> log.error("[Reconciliador Finanzas] Error inesperado: {}", e.getMessage())
                );
    }

    private Mono<Void> verificarYEjecutar() {
        return FirestoreReactiveUtils.toMono(
                db.collection(COL_CONFIG).document(DOC_FLAG).get())
                .flatMap(snap -> {
                    // Si ya se ejecutó correctamente, salir sin hacer nada
                    if (snap.exists() && Boolean.TRUE.equals(snap.getBoolean(CAMPO_OK))) {
                        log.info("[Reconciliador Finanzas] Ya ejecutado anteriormente — omitiendo barrido");
                        return Mono.empty();
                    }

                    // Primera vez (o ejecución fallida anterior) → ejecutar barrido
                    log.info("[Reconciliador Finanzas] Primera ejecución — iniciando barrido de contratos...");
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

                    // Verificar si ya tiene factura (idempotencia por si se interrumpió)
                    return FirestoreReactiveUtils.toMono(
                            db.collection("facturas").document(contratoId).get())
                            .flatMap(facturaSnap -> {
                                if (facturaSnap.exists()) {
                                    log.debug("[Reconciliador] Factura ya existe — contratoId={}", contratoId);
                                    return Mono.just(0); // contabilizar como procesado (ya existía)
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
                }, 5) // concurrencia máxima: 5 contratos en paralelo
                .reduce(0, Integer::sum)
                .flatMap(totalCreadas -> marcarComoCompletado(totalCreadas))
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
