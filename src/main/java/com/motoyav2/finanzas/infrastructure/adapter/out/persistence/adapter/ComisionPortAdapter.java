package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.WriteBatch;
import com.motoyav2.finanzas.application.port.out.ComisionPort;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.ComisionDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComisionPortAdapter implements ComisionPort {

    private static final String COL = "finanzas_comisiones";
    private final Firestore db;

    @Override
    public Flux<ComisionVendedor> findByVendedor(String vendedorId) {
        return toFlux(db.collection(COL)
                .whereEqualTo("vendedorId", vendedorId)
                .orderBy("periodoFin", Query.Direction.DESCENDING)
                .get())
                .map(doc -> toComision(doc.toObject(ComisionDocument.class)));
    }

    @Override
    public Flux<ComisionVendedor> findByPagoId(String pagoId) {
        return toFlux(db.collection(COL).whereEqualTo("pagoId", pagoId).get())
                .map(doc -> toComision(doc.toObject(ComisionDocument.class)));
    }

    @Override
    public Flux<ComisionVendedor> findAll(String tiendaId, LocalDate fechaInicio, LocalDate fechaFin) {
        Query query = db.collection(COL).orderBy("periodoFin", Query.Direction.DESCENDING);

        if (tiendaId != null)    query = query.whereEqualTo("tiendaId", tiendaId);
        if (fechaInicio != null) query = query.whereGreaterThanOrEqualTo("periodoInicio", fechaInicio.toString());
        if (fechaFin != null)    query = query.whereLessThanOrEqualTo("periodoFin", fechaFin.toString());

        return toFlux(query.get())
                .map(doc -> toComision(doc.toObject(ComisionDocument.class)));
    }

    @Override
    public Mono<Void> marcarPagada(String comisionId) {
        String ahora = Instant.now().toString();
        return toMono(db.collection(COL).document(comisionId).update(Map.of(
                "estado",        EstadoPago.PAGADO.name(),
                "pagadoEn",      ahora,
                "actualizadoEn", ahora
        ))).then();
    }

    @Override
    public Mono<Void> marcarEnProceso(List<String> comisionIds, String pagoId) {
        if (comisionIds.isEmpty()) return Mono.empty();
        String ahora = Instant.now().toString();
        WriteBatch batch = db.batch();
        comisionIds.forEach(id ->
                batch.update(db.collection(COL).document(id), Map.of(
                        "estado",        EstadoPago.EN_PROCESO.name(),
                        "pagoId",        pagoId,
                        "actualizadoEn", ahora
                ))
        );
        return toMono(batch.commit())
                .doOnSuccess(v -> log.info("[ComisionPort] {} comisiones marcadas EN_PROCESO para pagoId={}", comisionIds.size(), pagoId))
                .then();
    }

    @Override
    public Mono<Void> marcarPagadasBatch(List<String> comisionIds) {
        if (comisionIds.isEmpty()) return Mono.empty();
        String ahora = Instant.now().toString();
        WriteBatch batch = db.batch();
        comisionIds.forEach(id ->
                batch.update(db.collection(COL).document(id), Map.of(
                        "estado",        EstadoPago.PAGADO.name(),
                        "pagadoEn",      ahora,
                        "actualizadoEn", ahora
                ))
        );
        return toMono(batch.commit())
                .doOnSuccess(v -> log.info("[ComisionPort] {} comisiones marcadas PAGADO en batch", comisionIds.size()))
                .then();
    }

    // ── Mapeo ─────────────────────────────────────────────────────────────────

    private EstadoPago parseEstadoPago(String val) {
        try { return val != null ? EstadoPago.valueOf(val) : EstadoPago.PENDIENTE; }
        catch (IllegalArgumentException e) { return EstadoPago.PENDIENTE; }
    }

    private LocalDateTime parseInstantAsLocalDateTime(String val) {
        if (val == null) return null;
        try { return Instant.parse(val).atZone(ZoneOffset.UTC).toLocalDateTime(); }
        catch (Exception e) {
            try { return LocalDateTime.parse(val); }
            catch (Exception ex) { return null; }
        }
    }

    private ComisionVendedor toComision(ComisionDocument doc) {
        return ComisionVendedor.builder()
                .id(doc.getId())
                .contratoId(doc.getContratoId())
                .solicitudId(doc.getSolicitudId())
                .clienteNombre(doc.getClienteNombre())
                .clienteDocumento(doc.getClienteDocumento())
                .vendedorId(doc.getVendedorId())
                .vendedorNombre(doc.getVendedorNombre())
                .vendedorEmail(doc.getVendedorEmail())
                .vendedorPhone(doc.getVendedorPhone())
                .vendedorDocumento(doc.getVendedorDocumento())
                .vendedorTipoDocumento(doc.getVendedorTipoDocumento())
                .vendedorUserType(doc.getVendedorUserType())
                .tiendaId(doc.getTiendaId())
                .tiendaNombre(doc.getTiendaNombre())
                .periodoInicio(doc.getPeriodoInicio() != null ? LocalDate.parse(doc.getPeriodoInicio()) : null)
                .periodoFin(doc.getPeriodoFin() != null ? LocalDate.parse(doc.getPeriodoFin()) : null)
                .totalVentas(doc.getTotalVentas() != null ? doc.getTotalVentas() : 0)
                .montoComision(doc.getMontoComision() != null ? BigDecimal.valueOf(doc.getMontoComision()) : BigDecimal.ZERO)
                .estado(parseEstadoPago(doc.getEstado()))
                .pagoId(doc.getPagoId())
                .pagadoEn(parseInstantAsLocalDateTime(doc.getPagadoEn()))
                .build();
    }
}
