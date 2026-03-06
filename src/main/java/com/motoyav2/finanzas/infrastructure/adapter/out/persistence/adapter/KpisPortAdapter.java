package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.motoyav2.finanzas.application.port.out.KpisPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class KpisPortAdapter implements KpisPort {

    private static final String COL = "finanzas_kpis";
    private static final String DOC = "current";

    private final Firestore db;

    @Override
    public Mono<Map<String, Object>> obtenerKpis() {
        return toMono(db.collection(COL).document(DOC).get())
                .map(snap -> snap.exists() ? snap.getData() : new HashMap<>());
    }

    @Override
    public Mono<Void> incrementar(Map<String, Object> incrementos) {
        Map<String, Object> updates = new HashMap<>(incrementos);
        updates.put("ultimaActualizacion", Instant.now().toString());
        return toMono(db.collection(COL).document(DOC).update(updates)).then();
    }

    @Override
    public Mono<Void> recalcularCompleto() {
        Mono<Long> factPendientes = toFlux(
                db.collection("facturas").whereNotEqualTo("estado", "PAGADO").get()
        ).count();

        Mono<Long> pagosVenc = toFlux(
                db.collectionGroup("pagos").whereEqualTo("estado", "VENCIDO").get()
        ).count();

        Mono<Long> comPendientes = toFlux(
                db.collection("comisiones").whereEqualTo("estado", "PENDIENTE").get()
        ).count();

        return Mono.zip(factPendientes, pagosVenc, comPendientes)
                .flatMap(tuple -> toMono(
                        db.collection(COL).document(DOC).update(Map.of(
                                "totalFacturasPendientes", tuple.getT1(),
                                "pagosVencidos", tuple.getT2(),
                                "comisionesPendientes", tuple.getT3(),
                                "ultimaActualizacion", Instant.now().toString()
                        ))
                )).then()
                .doOnSuccess(v -> log.info("[KPIs] Recálculo completo ejecutado"));
    }
}
