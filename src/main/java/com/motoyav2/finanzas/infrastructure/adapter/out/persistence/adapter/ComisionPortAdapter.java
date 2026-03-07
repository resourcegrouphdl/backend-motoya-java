package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.motoyav2.finanzas.application.port.out.ComisionPort;
import com.motoyav2.finanzas.domain.enums.EstadoPago;
import com.motoyav2.finanzas.domain.model.ComisionVendedor;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.ComisionDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils.*;

@Component
@RequiredArgsConstructor
public class ComisionPortAdapter implements ComisionPort {

    private static final String COL = "comisiones";
    private final Firestore db;

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
        return toMono(db.collection(COL).document(comisionId).update(Map.of(
                "estado", EstadoPago.PAGADO.name(),
                "pagadoEn", Instant.now().toString(),
                "actualizadoEn", Instant.now().toString()
        ))).then();
    }

    private ComisionVendedor toComision(ComisionDocument doc) {
        return ComisionVendedor.builder()
                .id(doc.getId())
                .contratoId(doc.getContratoId())
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
                .estado(doc.getEstado() != null ? EstadoPago.valueOf(doc.getEstado()) : EstadoPago.PENDIENTE)
                .pagadoEn(doc.getPagadoEn() != null ? LocalDateTime.parse(doc.getPagadoEn()) : null)
                .build();
    }
}
