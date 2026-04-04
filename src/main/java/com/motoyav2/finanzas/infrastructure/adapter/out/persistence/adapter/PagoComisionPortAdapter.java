package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.WriteBatch;
import com.motoyav2.finanzas.application.port.in.command.ConfirmarPagoComisionCommand;
import com.motoyav2.finanzas.application.port.out.PagoComisionPort;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.ComisionDocument;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.PagoComisionDocument;
import com.motoyav2.finanzas.domain.model.PagoComisionVendedor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PagoComisionPortAdapter implements PagoComisionPort {

    private static final String COL_PAGOS     = "pagos_comisiones_vendedor";
    private static final String COL_COMISION  = "finanzas_comisiones";
    private static final String COL_VENDEDOR  = "vendedor_profiles";
    private static final String COL_TIENDA    = "tienda_profiles";

    private final Firestore db;

    // ── Listar ────────────────────────────────────────────────────────────────

    @Override
    public Flux<PagoComisionVendedor> findAll(String vendedorId, String tiendaId, String estado) {
        Query q = db.collection(COL_PAGOS).orderBy("periodoCorte", Query.Direction.DESCENDING);
        if (vendedorId != null && !vendedorId.isBlank()) q = q.whereEqualTo("vendedorId", vendedorId);
        if (tiendaId   != null && !tiendaId.isBlank())   q = q.whereEqualTo("tiendaId", tiendaId);
        if (estado     != null && !estado.isBlank())     q = q.whereEqualTo("estado", estado);
        return toFlux(q.get()).map(snap -> toDomain(snap.toObject(PagoComisionDocument.class)));
    }

    // ── Obtener ───────────────────────────────────────────────────────────────

    @Override
    public Mono<PagoComisionVendedor> findById(String id) {
        return toMono(db.collection(COL_PAGOS).document(id).get())
                .filter(snap -> snap.exists())
                .map(snap -> toDomain(snap.toObject(PagoComisionDocument.class)));
    }

    // ── Confirmar pago ────────────────────────────────────────────────────────

    @Override
    public Mono<Void> confirmar(ConfirmarPagoComisionCommand cmd) {
        String ahora = Instant.now().toString();

        // 1. Leer el pago para obtener la lista de comisionIds
        return toMono(db.collection(COL_PAGOS).document(cmd.getPagoId()).get())
                .flatMap(snap -> {
                    if (!snap.exists()) return Mono.error(new RuntimeException("PagoComision no encontrado: " + cmd.getPagoId()));

                    @SuppressWarnings("unchecked")
                    List<String> raw = (List<String>) snap.get("comisionIds");
                    final List<String> comisionIds = raw != null ? raw : List.of();

                    // 2. WriteBatch: actualizar pago + todas las comisiones
                    WriteBatch batch = db.batch();

                    // Actualizar el documento de pago
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("estado",          "PAGADO");
                    updates.put("metodoPago",      cmd.getMetodoPago());
                    updates.put("entidadBancaria", cmd.getEntidadBancaria());
                    updates.put("cuentaDestino",   cmd.getCuentaDestino());
                    updates.put("numeroOperacion", cmd.getNumeroOperacion());
                    updates.put("voucherUrl",      cmd.getVoucherUrl());
                    updates.put("voucherGcsPath",  cmd.getVoucherGcsPath());
                    updates.put("registradoPor",   cmd.getRegistradoPor());
                    updates.put("pagadoEn",        ahora);
                    updates.put("actualizadoEn",   ahora);
                    batch.update(db.collection(COL_PAGOS).document(cmd.getPagoId()), updates);

                    // Actualizar cada comisión individual → PAGADO
                    for (String comisionId : comisionIds) {
                        batch.update(db.collection(COL_COMISION).document(comisionId), Map.of(
                                "estado",        EstadoPago.PAGADO.name(),
                                "pagadoEn",      ahora,
                                "actualizadoEn", ahora
                        ));
                    }

                    return toMono(batch.commit())
                            .doOnSuccess(v -> log.info("[PagoComision] Confirmado pagoId={} comisiones={}", cmd.getPagoId(), comisionIds.size()))
                            .then();
                });
    }

    // ── Actualizar URL comprobante ────────────────────────────────────────────

    @Override
    public Mono<Void> actualizarComprobanteUrl(String pagoId, String comprobanteUrl) {
        return toMono(db.collection(COL_PAGOS).document(pagoId).update(Map.of(
                "comprobanteUrl", comprobanteUrl,
                "actualizadoEn",  Instant.now().toString()
        ))).then();
    }

    // ── Generar pagos quincenales ──────────────────────────────────────────────

    @Override
    public Mono<Integer> generarPagosQuincenales() {
        LocalDate hoy = LocalDate.now();
        int dia = hoy.getDayOfMonth();

        if (dia != 1 && dia != 15) {
            log.warn("[QuincenaComision] generarPagosQuincenales llamado en día {} — solo aplica días 1 y 15", dia);
        }

        final String tipoPeriodo;
        final LocalDate periodoDesde;
        final LocalDate periodoHasta;
        final String periodoCorte = hoy.toString();

        if (dia == 15) {
            tipoPeriodo  = "PRIMERA_QUINCENA";
            periodoDesde = hoy.withDayOfMonth(1);
            periodoHasta = hoy.withDayOfMonth(14);
        } else {
            // día 1 (o trigger manual fuera de fecha)
            tipoPeriodo  = "SEGUNDA_QUINCENA";
            LocalDate mesAnterior = hoy.minusMonths(1);
            periodoDesde = mesAnterior.withDayOfMonth(16);
            periodoHasta = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth());
        }

        log.info("[QuincenaComision] Generando para período {} — {} a {}", tipoPeriodo, periodoDesde, periodoHasta);

        final LocalDate finalPeriodoHasta = periodoHasta;

        return toFlux(db.collection(COL_COMISION).whereEqualTo("estado", "PENDIENTE").get())
                .map(snap -> snap.toObject(ComisionDocument.class))
                .filter(c -> {
                    if (c.getPeriodoFin() == null) return false;
                    LocalDate fecha = LocalDate.parse(c.getPeriodoFin());
                    return !fecha.isBefore(periodoDesde) && !fecha.isAfter(finalPeriodoHasta);
                })
                .collectList()
                .flatMapMany(comisiones -> {
                    Map<String, List<ComisionDocument>> porVendedor = comisiones.stream()
                            .collect(Collectors.groupingBy(ComisionDocument::getVendedorId));
                    log.info("[QuincenaComision] {} vendedores con comisiones pendientes", porVendedor.size());
                    return Flux.fromIterable(porVendedor.entrySet());
                })
                .flatMap(entry -> procesarGrupoVendedor(
                        entry.getKey(), entry.getValue(),
                        tipoPeriodo, periodoDesde.toString(), periodoHasta.toString(), periodoCorte
                ))
                .reduce(0, Integer::sum);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Mono<Integer> procesarGrupoVendedor(
            String vendedorId,
            List<ComisionDocument> comisiones,
            String tipoPeriodo,
            String periodoDesde,
            String periodoHasta,
            String periodoCorte) {

        String pagoId = vendedorId + "-" + periodoCorte;

        // Idempotencia
        return toMono(db.collection(COL_PAGOS).document(pagoId).get())
                .flatMap(snap -> {
                    if (snap.exists()) {
                        log.debug("[QuincenaComision] Pago ya existe vendedor={} periodo={}", vendedorId, periodoCorte);
                        return Mono.just(0);
                    }
                    return toMono(db.collection(COL_VENDEDOR).document(vendedorId).get())
                            .flatMap(vendedorSnap -> {
                                if (!vendedorSnap.exists()) {
                                    log.warn("[QuincenaComision] vendedor_profiles no encontrado id={}", vendedorId);
                                    return Mono.just(0);
                                }
                                String tiendaId = nvl(vendedorSnap.getString("tiendaId"));
                                return toMono(db.collection(COL_TIENDA).document(tiendaId).get())
                                        .flatMap(tiendaSnap -> crearBatchPago(
                                                pagoId, vendedorId, comisiones, vendedorSnap, tiendaSnap,
                                                tipoPeriodo, periodoDesde, periodoHasta, periodoCorte
                                        ));
                            });
                })
                .onErrorResume(e -> {
                    log.error("[QuincenaComision] Error procesando vendedor={}: {}", vendedorId, e.getMessage());
                    return Mono.just(0);
                });
    }

    private Mono<Integer> crearBatchPago(
            String pagoId,
            String vendedorId,
            List<ComisionDocument> comisiones,
            com.google.cloud.firestore.DocumentSnapshot vendedorSnap,
            com.google.cloud.firestore.DocumentSnapshot tiendaSnap,
            String tipoPeriodo,
            String periodoDesde,
            String periodoHasta,
            String periodoCorte) {

        String ahora = Instant.now().toString();
        List<String> comisionIds = comisiones.stream().map(ComisionDocument::getId).toList();
        double montoTotal = comisiones.stream()
                .mapToDouble(c -> c.getMontoComision() != null ? c.getMontoComision() : 0.0)
                .sum();

        String tiendaNombre = nvl(tiendaSnap.getString("businessName"));
        if (tiendaNombre.isBlank()) tiendaNombre = nvl(tiendaSnap.getString("name"));

        Map<String, Object> pago = new HashMap<>();
        pago.put("id",                   pagoId);
        pago.put("vendedorId",           vendedorId);
        pago.put("vendedorNombre",       (nvl(vendedorSnap.getString("lastName")) + " " + nvl(vendedorSnap.getString("firstName"))).trim());
        pago.put("vendedorDocumento",    nvl(vendedorSnap.getString("documentNumber")));
        pago.put("vendedorTipoDocumento",nvl(vendedorSnap.getString("documentType")));
        pago.put("vendedorEmail",        nvl(vendedorSnap.getString("email")));
        pago.put("vendedorPhone",        nvl(vendedorSnap.getString("phone")));
        pago.put("tiendaId",             nvl(vendedorSnap.getString("tiendaId")));
        pago.put("tiendaNombre",         tiendaNombre);
        pago.put("periodoCorte",         periodoCorte);
        pago.put("tipoPeriodo",          tipoPeriodo);
        pago.put("periodoDesde",         periodoDesde);
        pago.put("periodoHasta",         periodoHasta);
        pago.put("comisionIds",          comisionIds);
        pago.put("totalVentas",          comisiones.size());
        pago.put("montoTotal",           montoTotal);
        pago.put("metodoPago",           null);
        pago.put("entidadBancaria",      null);
        pago.put("cuentaDestino",        null);
        pago.put("numeroOperacion",      null);
        pago.put("voucherUrl",           null);
        pago.put("voucherGcsPath",       null);
        pago.put("estado",               "PENDIENTE");
        pago.put("registradoPor",        null);
        pago.put("creadoEn",             ahora);
        pago.put("pagadoEn",             null);
        pago.put("actualizadoEn",        ahora);

        // WriteBatch: crear pago + marcar comisiones EN_PROCESO
        WriteBatch batch = db.batch();
        batch.set(db.collection(COL_PAGOS).document(pagoId), pago);
        comisionIds.forEach(id ->
                batch.update(db.collection(COL_COMISION).document(id), Map.of(
                        "estado",        EstadoPago.EN_PROCESO.name(),
                        "pagoId",        pagoId,
                        "actualizadoEn", ahora
                ))
        );

        return toMono(batch.commit())
                .doOnSuccess(v -> log.info("[QuincenaComision] Batch creado pagoId={} vendedor={} motos={} monto={}",
                        pagoId, pago.get("vendedorNombre"), comisionIds.size(), montoTotal))
                .thenReturn(1);
    }

    private String nvl(String val) {
        return val != null ? val : "";
    }

    // ── Mapeo documento → dominio ─────────────────────────────────────────────

    private PagoComisionVendedor toDomain(PagoComisionDocument doc) {
        return PagoComisionVendedor.builder()
                .id(doc.getId())
                .vendedorId(doc.getVendedorId())
                .vendedorNombre(doc.getVendedorNombre())
                .vendedorDocumento(doc.getVendedorDocumento())
                .vendedorTipoDocumento(doc.getVendedorTipoDocumento())
                .vendedorEmail(doc.getVendedorEmail())
                .vendedorPhone(doc.getVendedorPhone())
                .tiendaId(doc.getTiendaId())
                .tiendaNombre(doc.getTiendaNombre())
                .periodoCorte(doc.getPeriodoCorte())
                .tipoPeriodo(doc.getTipoPeriodo())
                .periodoDesde(doc.getPeriodoDesde())
                .periodoHasta(doc.getPeriodoHasta())
                .comisionIds(doc.getComisionIds() != null ? doc.getComisionIds() : List.of())
                .totalVentas(doc.getTotalVentas() != null ? doc.getTotalVentas() : 0)
                .montoTotal(doc.getMontoTotal() != null ? BigDecimal.valueOf(doc.getMontoTotal()) : BigDecimal.ZERO)
                .metodoPago(doc.getMetodoPago())
                .entidadBancaria(doc.getEntidadBancaria())
                .cuentaDestino(doc.getCuentaDestino())
                .numeroOperacion(doc.getNumeroOperacion())
                .voucherUrl(doc.getVoucherUrl())
                .voucherGcsPath(doc.getVoucherGcsPath())
                .comprobanteUrl(doc.getComprobanteUrl())
                .estado(doc.getEstado())
                .registradoPor(doc.getRegistradoPor())
                .creadoEn(doc.getCreadoEn())
                .pagadoEn(doc.getPagadoEn())
                .actualizadoEn(doc.getActualizadoEn())
                .build();
    }
}
