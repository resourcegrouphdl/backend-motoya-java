package com.motoyav2.calculadora.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.motoyav2.calculadora.domain.model.*;
import com.motoyav2.calculadora.domain.port.out.ConfiguracionCrediticiaRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ConfiguracionRepositoryAdapter implements ConfiguracionCrediticiaRepository {

    private static final String COL = "configuracion_crediticia";
    private static final String DOC = "default";

    private final Firestore db;

    @Override
    public Mono<ConfiguracionCrediticia> findDefault() {
        return FirestoreUtils.toMono(db.collection(COL).document(DOC).get())
                .map(this::toDomain)
                .switchIfEmpty(Mono.just(buildDefault()));
    }

    @Override
    public Mono<ConfiguracionCrediticia> save(ConfiguracionCrediticia config) {
        Map<String, Object> data = toMap(config);
        return FirestoreUtils.toMono(db.collection(COL).document(DOC).set(data))
                .thenReturn(config);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private ConfiguracionCrediticia toDomain(DocumentSnapshot snap) {
        if (!snap.exists()) return buildDefault();

        // ── Plazos (backward-compatible: soporta campo legacy "meses") ────────
        List<PlazoTeaConfig> plazos = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawPlazos = (List<Map<String, Object>>) snap.get("plazos");
        if (rawPlazos != null) {
            for (Map<String, Object> p : rawPlazos) {
                int periodos;
                FrequenciaPago freq;
                if (p.containsKey("periodos")) {
                    periodos = FirestoreUtils.toInt(p.get("periodos"), 12);
                    String freqStr = p.getOrDefault("frecuencia", "MONTHLY").toString();
                    freq = FrequenciaPago.valueOf(freqStr);
                } else {
                    // Legacy: campo "meses" → asumir MONTHLY
                    periodos = FirestoreUtils.toInt(p.get("meses"), 12);
                    freq = FrequenciaPago.MONTHLY;
                }
                plazos.add(PlazoTeaConfig.builder()
                        .periodos(periodos)
                        .frecuencia(freq)
                        .tea(BigDecimal.valueOf(FirestoreUtils.toDouble(p.get("tea"), 0.72)))
                        .etiqueta(p.getOrDefault("etiqueta", "").toString())
                        .build());
            }
        }

        // ── Comisión default ──────────────────────────────────────────────────
        ComisionConfig comisionDefault = readComisionDefault(snap);

        Timestamp ts        = snap.getTimestamp("actualizadoEn");
        Instant   actualizado = ts != null ? ts.toDate().toInstant() : Instant.now();

        return ConfiguracionCrediticia.builder()
                .id(DOC)
                .gastosAdministrativos(bd(snap, "gastosAdministrativos", 890.0))
                .porcentajeInicialMinima(bd(snap, "porcentajeInicialMinima", 0.20))
                .montoMaximoFinanciar(bd(snap, "montoMaximoFinanciar", 5400.0))
                .montoMinimoFinanciar(bd(snap, "montoMinimoFinanciar", 500.0))
                .tasaSeguroDesgravamenMensual(bd(snap, "tasaSeguroDesgravamenMensual", 0.0004))
                .comisionDefault(comisionDefault)
                .teaDefault(bd(snap, "teaDefault", 0.72))
                .plazos(plazos.isEmpty() ? defaultPlazos() : plazos)
                .actualizadoEn(actualizado)
                .actualizadoPor(snap.getString("actualizadoPor"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private ComisionConfig readComisionDefault(DocumentSnapshot snap) {
        Object raw = snap.get("comisionDefault");
        if (raw instanceof Map<?, ?> map) {
            Object tipoRaw = map.get("tipo");
            String tipo = tipoRaw != null ? tipoRaw.toString() : "FIXED";
            double valor = FirestoreUtils.toDouble(map.get("valor"), 0.0);
            boolean financiada = Boolean.TRUE.equals(map.get("financiada"));
            return ComisionConfig.builder()
                    .tipo(TipoComision.valueOf(tipo))
                    .valor(BigDecimal.valueOf(valor))
                    .financiada(financiada)
                    .build();
        }
        // Legacy: campo "comisionDesembolso" como monto fijo no financiado
        Object legacyV = snap.get("comisionDesembolso");
        if (legacyV != null) {
            double valor = FirestoreUtils.toDouble(legacyV, 0.0);
            return ComisionConfig.builder()
                    .tipo(TipoComision.FIXED)
                    .valor(BigDecimal.valueOf(valor))
                    .financiada(false)
                    .build();
        }
        return ComisionConfig.builder()
                .tipo(TipoComision.FIXED)
                .valor(BigDecimal.ZERO)
                .financiada(false)
                .build();
    }

    private Map<String, Object> toMap(ConfiguracionCrediticia c) {
        List<Map<String, Object>> plazos = new ArrayList<>();
        if (c.getPlazos() != null) {
            for (PlazoTeaConfig p : c.getPlazos()) {
                Map<String, Object> pm = new HashMap<>();
                pm.put("periodos",   (long) p.getPeriodos());
                pm.put("frecuencia", p.getFrecuencia().name());
                pm.put("tea",        p.getTea().doubleValue());
                pm.put("etiqueta",   p.getEtiqueta() != null ? p.getEtiqueta() : "");
                plazos.add(pm);
            }
        }

        Map<String, Object> comisionMap = new HashMap<>();
        if (c.getComisionDefault() != null) {
            ComisionConfig cc = c.getComisionDefault();
            comisionMap.put("tipo",       cc.getTipo().name());
            comisionMap.put("valor",      cc.getValor().doubleValue());
            comisionMap.put("financiada", cc.isFinanciada());
        } else {
            comisionMap.put("tipo",       "FIXED");
            comisionMap.put("valor",      0.0);
            comisionMap.put("financiada", false);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("gastosAdministrativos",       c.getGastosAdministrativos().doubleValue());
        data.put("porcentajeInicialMinima",      c.getPorcentajeInicialMinima().doubleValue());
        data.put("montoMaximoFinanciar",         c.getMontoMaximoFinanciar().doubleValue());
        data.put("montoMinimoFinanciar",         c.getMontoMinimoFinanciar().doubleValue());
        data.put("tasaSeguroDesgravamenMensual", c.getTasaSeguroDesgravamenMensual().doubleValue());
        data.put("comisionDefault",              comisionMap);
        data.put("teaDefault",                   c.getTeaDefault().doubleValue());
        data.put("plazos",                       plazos);
        data.put("actualizadoEn",                Timestamp.ofTimeSecondsAndNanos(
                c.getActualizadoEn().getEpochSecond(), c.getActualizadoEn().getNano()));
        data.put("actualizadoPor",               c.getActualizadoPor());
        return data;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BigDecimal bd(DocumentSnapshot snap, String field, double def) {
        Object v = snap.get(field);
        return BigDecimal.valueOf(FirestoreUtils.toDouble(v, def));
    }

    private static ConfiguracionCrediticia buildDefault() {
        return ConfiguracionCrediticia.builder()
                .id(DOC)
                .gastosAdministrativos(BigDecimal.valueOf(890))
                .porcentajeInicialMinima(new BigDecimal("0.20"))
                .montoMaximoFinanciar(BigDecimal.valueOf(5400))
                .montoMinimoFinanciar(BigDecimal.valueOf(500))
                .tasaSeguroDesgravamenMensual(new BigDecimal("0.0004"))
                .comisionDefault(ComisionConfig.builder()
                        .tipo(TipoComision.FIXED)
                        .valor(BigDecimal.ZERO)
                        .financiada(false)
                        .build())
                .teaDefault(new BigDecimal("0.72"))
                .plazos(defaultPlazos())
                .actualizadoEn(Instant.now())
                .actualizadoPor("sistema")
                .build();
    }

    private static List<PlazoTeaConfig> defaultPlazos() {
        return List.of(
                PlazoTeaConfig.builder().periodos(8).frecuencia(FrequenciaPago.MONTHLY)
                        .tea(new BigDecimal("0.6526")).etiqueta("Pago rápido").build(),
                PlazoTeaConfig.builder().periodos(10).frecuencia(FrequenciaPago.MONTHLY)
                        .tea(new BigDecimal("0.7263")).etiqueta("Recomendado").build(),
                PlazoTeaConfig.builder().periodos(12).frecuencia(FrequenciaPago.MONTHLY)
                        .tea(new BigDecimal("0.7919")).etiqueta("Cuota menor").build(),
                PlazoTeaConfig.builder().periodos(32).frecuencia(FrequenciaPago.WEEKLY)
                        .tea(new BigDecimal("0.6526")).etiqueta("Pago rápido").build(),
                PlazoTeaConfig.builder().periodos(40).frecuencia(FrequenciaPago.WEEKLY)
                        .tea(new BigDecimal("0.7263")).etiqueta("Recomendado").build(),
                PlazoTeaConfig.builder().periodos(48).frecuencia(FrequenciaPago.WEEKLY)
                        .tea(new BigDecimal("0.7919")).etiqueta("Cuota menor").build()
        );
    }
}