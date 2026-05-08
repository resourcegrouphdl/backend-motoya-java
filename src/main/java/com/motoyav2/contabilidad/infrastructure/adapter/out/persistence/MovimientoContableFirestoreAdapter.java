package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.motoyav2.contabilidad.domain.model.MovimientoContable;
import com.motoyav2.contabilidad.domain.model.TipoMovimientoContable;
import com.motoyav2.contabilidad.domain.port.out.MovimientoContablePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovimientoContableFirestoreAdapter implements MovimientoContablePort {

    private static final String COL  = "contabilidad_movimientos";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final Firestore firestore;

    @Override
    public Mono<Boolean> existsByReferenciaId(String referenciaId) {
        return toMono(firestore.collection(COL).document(referenciaId).get())
                .map(snap -> snap.exists())
                .onErrorReturn(false);
    }

    @Override
    public Mono<Void> save(MovimientoContable m) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", m.getId());
        data.put("tipo", m.getTipo().name());
        data.put("contratoId", m.getContratoId());
        data.put("tiendaId", m.getTiendaId());
        data.put("referenciaId", m.getReferenciaId());
        data.put("periodo", m.getPeriodo());
        data.put("montoTotal", m.getMontoTotal());
        data.put("montoCapital", m.getMontoCapital());
        data.put("montoInteres", m.getMontoInteres());
        data.put("montoCosto", m.getMontoCosto());
        data.put("creadoEn", Date.from(m.getCreadoEn()));

        return toMono(firestore.collection(COL).document(m.getReferenciaId()).set(data))
                .then()
                .onErrorResume(e -> {
                    log.error("[CONTABILIDAD] Error guardando movimiento ref={}: {}", m.getReferenciaId(), e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Flux<MovimientoContable> findByPeriodo(LocalDate desde, LocalDate hasta, String tiendaId) {
        return queryByPeriodo(desde, hasta, tiendaId, null);
    }

    @Override
    public Flux<MovimientoContable> findByPeriodoYTipo(LocalDate desde, LocalDate hasta,
                                                       String tiendaId, TipoMovimientoContable tipo) {
        return queryByPeriodo(desde, hasta, tiendaId, tipo);
    }

    private Flux<MovimientoContable> queryByPeriodo(LocalDate desde, LocalDate hasta,
                                                    String tiendaId, TipoMovimientoContable tipo) {
        Date inicio = Date.from(desde.atStartOfDay(LIMA).toInstant());
        Date fin    = Date.from(hasta.plusDays(1).atStartOfDay(LIMA).toInstant());

        Query query = firestore.collection(COL)
                .whereGreaterThanOrEqualTo("creadoEn", inicio)
                .whereLessThan("creadoEn", fin);

        return toMono(query.get())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .filter(doc -> tiendaId == null || tiendaId.isBlank()
                        || tiendaId.equals(doc.getString("tiendaId")))
                .filter(doc -> tipo == null
                        || tipo.name().equals(doc.getString("tipo")))
                .map(doc -> {
                    Date creadoEnDate = doc.getDate("creadoEn");
                    return MovimientoContable.builder()
                            .id(doc.getId())
                            .tipo(parseTipo(doc.getString("tipo")))
                            .contratoId(str(doc.getString("contratoId")))
                            .tiendaId(str(doc.getString("tiendaId")))
                            .referenciaId(str(doc.getString("referenciaId")))
                            .periodo(str(doc.getString("periodo")))
                            .montoTotal(dbl(doc.getDouble("montoTotal")))
                            .montoCapital(dbl(doc.getDouble("montoCapital")))
                            .montoInteres(dbl(doc.getDouble("montoInteres")))
                            .montoCosto(dbl(doc.getDouble("montoCosto")))
                            .creadoEn(creadoEnDate != null ? creadoEnDate.toInstant() : null)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("[CONTABILIDAD] Error leyendo movimientos: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private TipoMovimientoContable parseTipo(String s) {
        try { return TipoMovimientoContable.valueOf(s); }
        catch (Exception e) { return TipoMovimientoContable.INGRESO_CUOTA; }
    }

    private String str(String v) { return v != null ? v : ""; }
    private double dbl(Double v) { return v != null ? v : 0.0; }
}
