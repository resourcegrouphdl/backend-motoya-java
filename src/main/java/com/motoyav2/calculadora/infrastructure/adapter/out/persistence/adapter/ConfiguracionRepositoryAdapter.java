package com.motoyav2.calculadora.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.motoyav2.calculadora.domain.model.ConfiguracionCrediticia;
import com.motoyav2.calculadora.domain.model.PlazoTeaConfig;
import com.motoyav2.calculadora.domain.port.out.ConfiguracionCrediticiaRepository;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        List<PlazoTeaConfig> plazos = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawPlazos = (List<Map<String, Object>>) snap.get("plazos");
        if (rawPlazos != null) {
            for (Map<String, Object> p : rawPlazos) {
                plazos.add(PlazoTeaConfig.builder()
                        .meses(FirestoreUtils.toInt(p.get("meses"), 12))
                        .tea(BigDecimal.valueOf(FirestoreUtils.toDouble(p.get("tea"), 0.72)))
                        .etiqueta(p.getOrDefault("etiqueta", "").toString())
                        .build());
            }
        }

        Timestamp ts = snap.getTimestamp("actualizadoEn");
        Instant   actualizado = ts != null ? ts.toDate().toInstant() : Instant.now();

        return ConfiguracionCrediticia.builder()
                .id(DOC)
                .gastosAdministrativos(bd(snap, "gastosAdministrativos", 890.0))
                .porcentajeInicialMinima(bd(snap, "porcentajeInicialMinima", 0.20))
                .montoMaximoFinanciar(bd(snap, "montoMaximoFinanciar", 5400.0))
                .montoMinimoFinanciar(bd(snap, "montoMinimoFinanciar", 500.0))
                .tasaSeguroDesgravamenMensual(bd(snap, "tasaSeguroDesgravamenMensual", 0.0004))
                .comisionDesembolso(bd(snap, "comisionDesembolso", 0.0))
                .teaDefault(bd(snap, "teaDefault", 0.72))
                .plazos(plazos.isEmpty() ? defaultPlazos() : plazos)
                .actualizadoEn(actualizado)
                .actualizadoPor(snap.getString("actualizadoPor"))
                .build();
    }

    private Map<String, Object> toMap(ConfiguracionCrediticia c) {
        List<Map<String, Object>> plazos = new ArrayList<>();
        if (c.getPlazos() != null) {
            for (PlazoTeaConfig p : c.getPlazos()) {
                Map<String, Object> pm = new HashMap<>();
                pm.put("meses",    (long) p.getMeses());
                pm.put("tea",      p.getTea().doubleValue());
                pm.put("etiqueta", p.getEtiqueta() != null ? p.getEtiqueta() : "");
                plazos.add(pm);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("gastosAdministrativos",       c.getGastosAdministrativos().doubleValue());
        data.put("porcentajeInicialMinima",      c.getPorcentajeInicialMinima().doubleValue());
        data.put("montoMaximoFinanciar",         c.getMontoMaximoFinanciar().doubleValue());
        data.put("montoMinimoFinanciar",         c.getMontoMinimoFinanciar().doubleValue());
        data.put("tasaSeguroDesgravamenMensual", c.getTasaSeguroDesgravamenMensual().doubleValue());
        data.put("comisionDesembolso",           c.getComisionDesembolso().doubleValue());
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

    /** Config por defecto (usada si el documento aún no existe en Firestore). */
    private static ConfiguracionCrediticia buildDefault() {
        return ConfiguracionCrediticia.builder()
                .id(DOC)
                .gastosAdministrativos(BigDecimal.valueOf(890))
                .porcentajeInicialMinima(new BigDecimal("0.20"))
                .montoMaximoFinanciar(BigDecimal.valueOf(5400))
                .montoMinimoFinanciar(BigDecimal.valueOf(500))
                .tasaSeguroDesgravamenMensual(new BigDecimal("0.0004"))
                .comisionDesembolso(BigDecimal.ZERO)
                .teaDefault(new BigDecimal("0.72"))
                .plazos(defaultPlazos())
                .actualizadoEn(Instant.now())
                .actualizadoPor("sistema")
                .build();
    }

    private static List<PlazoTeaConfig> defaultPlazos() {
        return List.of(
                PlazoTeaConfig.builder().meses(8).tea(new BigDecimal("0.6526")).etiqueta("Pago rápido").build(),
                PlazoTeaConfig.builder().meses(10).tea(new BigDecimal("0.7263")).etiqueta("Recomendado").build(),
                PlazoTeaConfig.builder().meses(12).tea(new BigDecimal("0.7919")).etiqueta("Cuota menor").build()
        );
    }
}
