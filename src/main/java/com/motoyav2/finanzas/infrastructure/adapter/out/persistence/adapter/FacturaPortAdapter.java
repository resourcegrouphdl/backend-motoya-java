package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.motoyav2.finanzas.application.port.out.FacturaPort;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.domain.enums.MetodoPago;
import com.motoyav2.finanzas.domain.enums.TipoConceptoPago;
import com.motoyav2.finanzas.domain.model.Factura;
import com.motoyav2.finanzas.domain.model.PagoFactura;
import com.motoyav2.finanzas.infrastructure.adapter.in.web.dto.request.FiltrosFacturaRequest;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.FacturaDocument;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.PagoDocument;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils.*;

@Component
@RequiredArgsConstructor
public class FacturaPortAdapter implements FacturaPort {

    private static final String COL_FACTURAS = "facturas";
    private static final String COL_PAGOS    = "pagos";

    private final Firestore db;

    @Override
    public Flux<Factura> findAll(FiltrosFacturaRequest filtros) {
        var query = (com.google.cloud.firestore.Query) db.collection(COL_FACTURAS)
                .orderBy("fechaFactura", Query.Direction.DESCENDING);

        if (filtros.getTiendaId() != null)
            query = query.whereEqualTo("tiendaId", filtros.getTiendaId());
        if (filtros.getEstado() != null)
            query = query.whereEqualTo("estado", filtros.getEstado().name());
        if (filtros.getFechaDesde() != null)
            query = query.whereGreaterThanOrEqualTo("fechaFactura", filtros.getFechaDesde().toString());
        if (filtros.getFechaHasta() != null)
            query = query.whereLessThanOrEqualTo("fechaFactura", filtros.getFechaHasta().toString());

        return toFlux(query.get())
                .map(doc -> doc.toObject(FacturaDocument.class))
                .flatMap(this::enrichWithPagos);
    }

    @Override
    public Mono<Factura> findById(String facturaId) {
        return toMono(db.collection(COL_FACTURAS).document(facturaId).get())
                .flatMap(snap -> {
                    if (!snap.exists()) return Mono.empty();
                    FacturaDocument doc = snap.toObject(FacturaDocument.class);
                    return enrichWithPagos(doc);
                });
    }

    @Override
    public Flux<PagoFactura> findPagosByFacturaId(String facturaId) {
        return toFlux(db.collection(COL_FACTURAS).document(facturaId)
                .collection(COL_PAGOS).orderBy("numero").get())
                .map(doc -> toPagoFactura(doc.toObject(PagoDocument.class)));
    }

    @Override
    public Mono<Void> registrarPago(String facturaId, String pagoId, Map<String, Object> campos) {
        return toMono(db.collection(COL_FACTURAS).document(facturaId)
                .collection(COL_PAGOS).document(pagoId).update(campos)).then();
    }

    @Override
    public Mono<Void> actualizarEstadoFactura(String facturaId, Map<String, Object> campos) {
        return toMono(db.collection(COL_FACTURAS).document(facturaId).update(campos)).then();
    }

    @Override
    public Mono<Void> actualizarVoucherUrl(String facturaId, String pagoId, String url) {
        return toMono(db.collection(COL_FACTURAS).document(facturaId)
                .collection(COL_PAGOS).document(pagoId).update("voucherUrl", url)).then();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Mono<Factura> enrichWithPagos(FacturaDocument doc) {
        return toFlux(db.collection(COL_FACTURAS).document(doc.getId())
                .collection(COL_PAGOS).orderBy("numero").get())
                .map(snap -> toPagoFactura(snap.toObject(PagoDocument.class)))
                .collectList()
                .map(pagos -> toFactura(doc, pagos));
    }

    private Factura toFactura(FacturaDocument doc, List<PagoFactura> pagos) {
        return Factura.builder()
                .id(doc.getId())
                .numero(doc.getNumero())
                .tiendaId(doc.getTiendaId())
                .tiendaNombre(doc.getTiendaNombre())
                .ventaId(doc.getVentaId())
                .clienteNombre(doc.getClienteNombre())
                .motoModelo(doc.getMotoModelo())
                .montoTotal(doc.getMontoTotal() != null ? BigDecimal.valueOf(doc.getMontoTotal()) : BigDecimal.ZERO)
                .fechaFactura(parseDate(doc.getFechaFactura()))
                .condicionPago(doc.getCondicionPago() != null ? doc.getCondicionPago() : 0)
                .estado(parseEstadoPago(doc.getEstado()))
                .pagos(pagos)
                .build();
    }

    private PagoFactura toPagoFactura(PagoDocument doc) {
        return PagoFactura.builder()
                .id(doc.getId())
                .facturaId(doc.getFacturaId())
                .numero(doc.getNumero() != null ? doc.getNumero() : 0)
                .concepto(doc.getConcepto() != null ? TipoConceptoPago.valueOf(doc.getConcepto()) : null)
                .monto(doc.getMonto() != null ? BigDecimal.valueOf(doc.getMonto()) : BigDecimal.ZERO)
                .fechaProgramada(parseDate(doc.getFechaProgramada()))
                .fechaPago(parseDate(doc.getFechaPago()))
                .estado(parseEstadoPago(doc.getEstado()))
                .voucherUrl(doc.getVoucherUrl())
                .metodoPago(doc.getMetodoPago() != null ? MetodoPago.valueOf(doc.getMetodoPago()) : null)
                .build();
    }

    private LocalDate parseDate(String val) {
        return val != null ? LocalDate.parse(val) : null;
    }

    private EstadoPago parseEstadoPago(String val) {
        try { return val != null ? EstadoPago.valueOf(val) : EstadoPago.PENDIENTE; }
        catch (IllegalArgumentException e) { return EstadoPago.PENDIENTE; }
    }
}
