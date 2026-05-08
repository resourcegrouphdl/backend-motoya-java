package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.contabilidad.domain.model.ContratoData;
import com.motoyav2.contabilidad.domain.port.out.ContratoDataPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContratoDataFirestoreAdapter implements ContratoDataPort {

    private static final String COL = "contratos";
    private final Firestore firestore;

    @Override
    public Flux<ContratoData> findTodos() {
        return toMono(firestore.collection(COL).get())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .map(this::toContratoData)
                .filter(c -> c.numeroCuotas() > 0 && c.montoFinanciado() > 0)
                .onErrorResume(e -> {
                    log.error("[CONTABILIDAD] Error leyendo contratos: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    @Override
    public Mono<ContratoData> findById(String contratoId) {
        return toMono(firestore.collection(COL).document(contratoId).get())
                .flatMap(snap -> snap.exists() ? Mono.just(toContratoData(snap)) : Mono.empty())
                .onErrorResume(e -> {
                    log.error("[CONTABILIDAD] Error leyendo contrato {}: {}", contratoId, e.getMessage());
                    return Mono.empty();
                });
    }

    @SuppressWarnings("unchecked")
    private ContratoData toContratoData(com.google.cloud.firestore.DocumentSnapshot snap) {
        String tiendaId = "";
        Object tiendaObj = snap.get("tienda");
        if (tiendaObj instanceof Map<?, ?> t) {
            Object idObj = t.get("id");
            tiendaId = idObj != null ? idObj.toString() : "";
        }

        // datosFinancieros es un mapa embebido
        double montoFinanciado = 0;
        double tasaInteres     = 0;
        int    numeroCuotas    = 0;

        Object dfObj = snap.get("datosFinancieros");
        if (dfObj instanceof Map<?, ?> df) {
            montoFinanciado = toDouble(df.get("montoFinanciado"));
            tasaInteres     = toDouble(df.get("tasaInteresAnual"));
            numeroCuotas    = toInt(df.get("numeroCuotas"));
        }

        return new ContratoData(snap.getId(), tiendaId, montoFinanciado, tasaInteres, numeroCuotas);
    }

    private double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }
}
