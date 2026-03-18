package com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.adapter;

import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.common.util.concurrent.MoreExecutors;
import com.motoyav2.evaluacion.application.port.out.EvaluacionQueryPort;
import com.motoyav2.evaluacion.domain.model.EvaluacionResumen;
import com.motoyav2.evaluacion.domain.model.ResultadoPaginado;
import com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.document.evaluacioncredito.EvaluacionCreditoDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Fuente de datos: ÚNICAMENTE la colección 'solicitudes'.
 * No se realizan joins con otras colecciones.
 * No se usa 'evaluacionDeCredito' (colección eliminada).
 */
@Component
@RequiredArgsConstructor
public class EvaluacionQueryAdapter implements EvaluacionQueryPort {

    private static final String COL_SOLICITUDES = "solicitudes";

    private final Firestore db;

    // ── Listar paginado ────────────────────────────────────────────────────

    @Override
    public Mono<ResultadoPaginado<EvaluacionResumen>> listarPaginado(int pagina, int porPagina) {
        int skip = (pagina - 1) * porPagina;

        return toFlux(db.collection(COL_SOLICITUDES).get())
                .collectList()
                .flatMap(snaps -> {
                    long total = snaps.size();
                    List<EvaluacionResumen> items = snaps.stream()
                            .skip(skip)
                            .limit(porPagina)
                            .map(this::mapToResumen)
                            .collect(Collectors.toList());
                    return Mono.just(new ResultadoPaginado<>(items, total, pagina, porPagina));
                });
    }

    private EvaluacionResumen mapToResumen(QueryDocumentSnapshot s) {
        // ── Tienda y Vendedor (embebidos en solicitud) ──
        String tiendaId     = "";
        String tiendaNombre = "";
        String vendNombre   = nvl(s.getString("vendedorNombre"));
        Object vendedorRaw  = s.get("vendedor");
        if (vendedorRaw instanceof Map<?, ?> v) {
            tiendaId     = asString(v.get("id"));
            tiendaNombre = asString(v.get("tienda"));
            if (vendNombre.isBlank()) vendNombre = asString(v.get("nombre"));
        }

        // ── Montos: estructura nueva primero, luego legacy ──
        BigDecimal montoVehiculo  = null;
        BigDecimal montoFinanciar = null;
        Double precioMoto = s.getDouble("precioCompraMoto");
        if (precioMoto != null) montoVehiculo = BigDecimal.valueOf(precioMoto);

        Object dfRaw = s.get("datosFinancieros");
        if (dfRaw instanceof Map<?, ?> df) {
            Object mv = df.get("montoVehiculo");
            if (mv instanceof Number n) montoVehiculo = BigDecimal.valueOf(n.doubleValue());
            Object mf = df.get("montoFinanciar");
            if (mf instanceof Number n) montoFinanciar = BigDecimal.valueOf(n.doubleValue());
        }
        if (montoFinanciar == null) {
            Double inicial = s.getDouble("inicial");
            if (precioMoto != null && inicial != null) {
                montoFinanciar = BigDecimal.valueOf(precioMoto - inicial);
            }
        }

        // ── Score final ──
        String scoreFinal = null;
        Double sfDouble = s.getDouble("scoreFinal");
        if (sfDouble != null) scoreFinal = String.valueOf(sfDouble);

        // ── Timestamps ──
        com.google.cloud.Timestamp cAt = s.getTimestamp("createdAt");
        com.google.cloud.Timestamp uAt = s.getTimestamp("updatedAt");

        String fiadorId = s.getString("fiadorId");

        return EvaluacionResumen.builder()
                .id(s.getId())
                .codigoSolicitud(nvl(s.getString("numeroSolicitud")))
                .estado(nvl(s.getString("estado")))
                .prioridad(nvl(s.getString("prioridad")))
                .scoreFinal(scoreFinal)
                .tiendaId(tiendaId)
                .tiendaNombre(tiendaNombre)
                .nombreEvaluador(vendNombre)            // vendedor = asesor de la tienda
                .asignadoA(nvl(s.getString("asesorAsignadoId")))
                .montoVehiculo(montoVehiculo)
                .montoFinanciar(montoFinanciar)
                .tieneFiador(fiadorId != null && !fiadorId.isBlank())
                .creadoEn(cAt != null ? cAt.toDate().toInstant().toString() : null)
                .actualizadoEn(uAt != null ? uAt.toDate().toInstant().toString() : null)
                .build();
    }

    // ── buscarPorEvaluacionId ──────────────────────────────────────────────

    @Override
    public Mono<EvaluacionCreditoDocument> buscarPorEvaluacionId(String evaluacionId) {
        return toMono(db.collection(COL_SOLICITUDES).document(evaluacionId).get())
                .filter(DocumentSnapshot::exists)
                .map(snap -> {
                    Object vendedorRaw = snap.get("vendedor");
                    String tiendaId    = "";
                    String tiendaNom   = "";
                    String vendId      = nvl(snap.getString("vendedorId"));
                    String vendNom     = nvl(snap.getString("vendedorNombre"));
                    if (vendedorRaw instanceof Map<?, ?> v) {
                        tiendaId  = asString(v.get("id"));
                        tiendaNom = asString(v.get("tienda"));
                        if (vendId.isBlank())  vendId  = asString(v.get("id"));
                        if (vendNom.isBlank()) vendNom = asString(v.get("nombre"));
                    }
                    com.google.cloud.Timestamp cAt = snap.getTimestamp("createdAt");
                    com.google.cloud.Timestamp uAt = snap.getTimestamp("updatedAt");

                    return EvaluacionCreditoDocument.builder()
                            .codigoDeSolicitud(snap.getId())
                            .solicitudFirebaseId(snap.getId())
                            .estado(nvl(snap.getString("estado")))
                            .prioridad(nvl(snap.getString("prioridad")))
                            .scoreDocumental(snap.getDouble("scoreDocumental") != null
                                    ? String.valueOf(snap.getDouble("scoreDocumental")) : null)
                            .scoreFinal(snap.getDouble("scoreFinal") != null
                                    ? String.valueOf(snap.getDouble("scoreFinal")) : null)
                            .vendedorId(vendId)
                            .vendedorNombre(vendNom)
                            .tiendaId(tiendaId)
                            .tiendaNombre(tiendaNom)
                            .asignadoA(nvl(snap.getString("asesorAsignadoId")))
                            .decision(nvl(snap.getString("decisionFinal")))
                            .creadoEn(cAt != null ? cAt.toDate().toInstant().toString() : null)
                            .actualizadoEn(uAt != null ? uAt.toDate().toInstant().toString() : null)
                            .build();
                });
    }

    // ── Helpers reactivos ──────────────────────────────────────────────────

    private <T> Mono<T> toMono(com.google.api.core.ApiFuture<T> future) {
        return Mono.fromFuture(() -> {
            CompletableFuture<T> cf = new CompletableFuture<>();
            ApiFutures.addCallback(future, new ApiFutureCallback<T>() {
                @Override public void onSuccess(T r)  { cf.complete(r); }
                @Override public void onFailure(Throwable e) { cf.completeExceptionally(e); }
            }, MoreExecutors.directExecutor());
            return cf;
        });
    }

    private Flux<QueryDocumentSnapshot> toFlux(com.google.api.core.ApiFuture<QuerySnapshot> future) {
        return toMono(future).flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()));
    }

    private static String nvl(String v) { return v != null ? v : ""; }

    /**
     * Convierte de forma segura cualquier valor de un mapa Firestore a String.
     * Maneja: String, Number, Boolean, List (toma el primer elemento), null.
     */
    private static String asString(Object val) {
        if (val == null) return "";
        if (val instanceof String s) return s;
        if (val instanceof List<?> list) return list.isEmpty() ? "" : String.valueOf(list.get(0));
        return String.valueOf(val);
    }
}
