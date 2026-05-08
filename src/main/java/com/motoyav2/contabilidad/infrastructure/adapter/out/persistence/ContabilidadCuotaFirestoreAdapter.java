package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.contabilidad.domain.model.ContabilidadCuota;
import com.motoyav2.contabilidad.domain.model.DesgloseCuota;
import com.motoyav2.contabilidad.domain.port.out.ContabilidadCuotaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContabilidadCuotaFirestoreAdapter implements ContabilidadCuotaPort {

    private static final String COL = "contabilidad_cuotas";
    private final Firestore firestore;

    @Override
    public Mono<ContabilidadCuota> findByContratoId(String contratoId) {
        return toMono(firestore.collection(COL).document(contratoId).get())
                .flatMap(snap -> {
                    if (!snap.exists()) return Mono.empty();
                    return Mono.just(toModel(snap));
                })
                .onErrorResume(e -> {
                    log.error("[CONTABILIDAD] Error leyendo cuota contratoId={}: {}", contratoId, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> save(ContabilidadCuota cuota) {
        Map<String, Object> data = new HashMap<>();
        data.put("contratoId", cuota.getContratoId());
        data.put("tiendaId", cuota.getTiendaId());
        data.put("numeroCuotas", cuota.getNumeroCuotas());
        data.put("montoFinanciar", cuota.getMontoFinanciar());
        data.put("tasaInteres", cuota.getTasaInteres());
        data.put("interesTotal", cuota.getInteresTotal());
        data.put("capitalTotal", cuota.getCapitalTotal());
        data.put("calculadoEn", Date.from(cuota.getCalculadoEn()));

        List<Map<String, Object>> cuotasList = new ArrayList<>();
        for (DesgloseCuota dc : cuota.getCuotas()) {
            Map<String, Object> c = new HashMap<>();
            c.put("numero", dc.getNumero());
            c.put("fechaVencimiento", dc.getFechaVencimiento() != null
                    ? Date.from(dc.getFechaVencimiento()) : null);
            c.put("montoTotal", dc.getMontoTotal());
            c.put("montoCapital", dc.getMontoCapital());
            c.put("montoInteres", dc.getMontoInteres());
            cuotasList.add(c);
        }
        data.put("cuotas", cuotasList);

        return toMono(firestore.collection(COL).document(cuota.getContratoId()).set(data))
                .then()
                .onErrorResume(e -> {
                    log.error("[CONTABILIDAD] Error guardando cuota contratoId={}: {}", cuota.getContratoId(), e.getMessage());
                    return Mono.empty();
                });
    }

    @SuppressWarnings("unchecked")
    private ContabilidadCuota toModel(com.google.cloud.firestore.DocumentSnapshot snap) {
        List<DesgloseCuota> cuotas = new ArrayList<>();
        Object rawCuotas = snap.get("cuotas");
        if (rawCuotas instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> cm = (Map<String, Object>) m;
                    Object fechaObj = cm.get("fechaVencimiento");
                    Instant fecha = fechaObj instanceof Date d ? d.toInstant() : null;
                    cuotas.add(DesgloseCuota.builder()
                            .numero(((Number) cm.getOrDefault("numero", 0)).intValue())
                            .fechaVencimiento(fecha)
                            .montoTotal(toDouble(cm.get("montoTotal")))
                            .montoCapital(toDouble(cm.get("montoCapital")))
                            .montoInteres(toDouble(cm.get("montoInteres")))
                            .build());
                }
            }
        }

        Date calcEn = snap.getDate("calculadoEn");
        return ContabilidadCuota.builder()
                .contratoId(snap.getId())
                .tiendaId(snap.getString("tiendaId"))
                .numeroCuotas(snap.getLong("numeroCuotas") != null
                        ? snap.getLong("numeroCuotas").intValue() : 0)
                .montoFinanciar(toDouble(snap.getDouble("montoFinanciar")))
                .tasaInteres(toDouble(snap.getDouble("tasaInteres")))
                .interesTotal(toDouble(snap.getDouble("interesTotal")))
                .capitalTotal(toDouble(snap.getDouble("capitalTotal")))
                .cuotas(cuotas)
                .calculadoEn(calcEn != null ? calcEn.toInstant() : Instant.now())
                .build();
    }

    private double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
