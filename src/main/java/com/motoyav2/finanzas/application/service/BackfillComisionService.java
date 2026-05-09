package com.motoyav2.finanzas.application.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.motoyav2.finanzas.application.port.in.BackfillClienteComisionUseCase;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillComisionService implements BackfillClienteComisionUseCase {

    private static final String COL_COMISIONES = "finanzas_comisiones";
    private static final String COL_CONTRATOS  = "contratos";

    private final Firestore db;

    @Override
    public Mono<Integer> ejecutar() {
        log.info("[Backfill] Iniciando relleno de clienteNombre en comisiones");

        return FirestoreReactiveUtils.toMono(db.collection(COL_COMISIONES).get())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .filter(doc -> doc.getString("clienteNombre") == null
                            || doc.getString("clienteNombre").isBlank())
                .flatMap(this::enriquecerDesdeContrato, 4) // concurrencia controlada
                .reduce(0, Integer::sum)
                .doOnSuccess(n -> log.info("[Backfill] {} comisiones actualizadas", n));
    }

    private Mono<Integer> enriquecerDesdeContrato(QueryDocumentSnapshot comisionDoc) {
        String comisionId = comisionDoc.getId();
        String contratoId = comisionDoc.getString("contratoId");

        if (contratoId == null || contratoId.isBlank()) {
            log.warn("[Backfill] Comisión {} sin contratoId — omitiendo", comisionId);
            return Mono.just(0);
        }

        return FirestoreReactiveUtils.toMono(
                db.collection(COL_CONTRATOS).document(contratoId).get())
                .flatMap(contratoSnap -> {
                    if (!contratoSnap.exists()) {
                        log.warn("[Backfill] Contrato {} no encontrado para comisión {}", contratoId, comisionId);
                        return Mono.just(0);
                    }

                    String nombres   = nvl(contratoSnap.getString("titular.nombres"));
                    String apellidos = nvl(contratoSnap.getString("titular.apellidos"));
                    String documento = nvl(contratoSnap.getString("titular.numeroDocumento"));

                    // Firestore guarda objetos anidados — intentar acceso por campo compuesto
                    // y si no funciona, leer el mapa manualmente
                    if (nombres.isBlank() && apellidos.isBlank()) {
                        Object titularObj = contratoSnap.get("titular");
                        if (titularObj instanceof Map<?, ?> t) {
                            nombres   = nvl((String) t.get("nombres"));
                            apellidos = nvl((String) t.get("apellidos"));
                            documento = nvl((String) t.get("numeroDocumento"));
                        }
                    }

                    String clienteNombre = (apellidos + " " + nombres).trim();
                    if (clienteNombre.isBlank()) {
                        log.warn("[Backfill] Contrato {} sin datos de titular", contratoId);
                        return Mono.just(0);
                    }

                    String finalDoc = documento;
                    String finalNombre = clienteNombre;

                    return FirestoreReactiveUtils.toMono(
                            db.collection(COL_COMISIONES).document(comisionId).update(Map.of(
                                    "clienteNombre",    finalNombre,
                                    "clienteDocumento", finalDoc,
                                    "actualizadoEn",    Instant.now().toString()
                            )))
                            .thenReturn(1)
                            .doOnSuccess(v -> log.info("[Backfill] Comisión {} → cliente={}", comisionId, finalNombre));
                });
    }

    private String nvl(String v) {
        return v != null ? v : "";
    }
}
