package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.WriteBatch;
import com.motoyav2.finanzas.application.port.out.CuentaPorPagarPort;
import com.motoyav2.finanzas.domain.enums.EstadoCuenta;
import com.motoyav2.finanzas.domain.enums.TipoCuenta;
import com.motoyav2.finanzas.domain.model.CuentaPorPagar;
import com.motoyav2.finanzas.domain.model.CuotaCuenta;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.CuentaDocument;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.CuotaDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils.*;

@Component
@RequiredArgsConstructor
public class CuentaPortAdapter implements CuentaPorPagarPort {

    private static final String COL_CUENTAS = "cuentas_pagar";
    private static final String COL_CUOTAS  = "cuotas";

    private final Firestore db;

    @Override
    public Flux<CuentaPorPagar> findAll(TipoCuenta tipo, EstadoCuenta estado) {
        var col = db.collection(COL_CUENTAS);
        Query query = col.orderBy("fechaVencimiento", Query.Direction.ASCENDING);

        if (tipo != null)   query = query.whereEqualTo("tipo", tipo.name());
        if (estado != null) query = query.whereEqualTo("estado", estado.name());

        return toFlux(query.get())
                .map(doc -> doc.toObject(CuentaDocument.class))
                .flatMap(this::enrichWithCuotas);
    }

    @Override
    public Mono<CuentaPorPagar> save(CuentaPorPagar cuenta, List<CuotaCuenta> cuotas) {
        WriteBatch batch = db.batch();

        var cuentaRef = db.collection(COL_CUENTAS).document(cuenta.getId());
        batch.set(cuentaRef, toCuentaDocument(cuenta));

        for (CuotaCuenta cuota : cuotas) {
            var cuotaRef = cuentaRef.collection(COL_CUOTAS).document(cuota.getId());
            batch.set(cuotaRef, toCuotaDocument(cuota, cuenta));
        }

        return toMono(batch.commit()).thenReturn(cuenta);
    }

    @Override
    public Flux<CuotaCuenta> findCuotasByCuentaId(String cuentaId) {
        return toFlux(db.collection(COL_CUENTAS).document(cuentaId)
                .collection(COL_CUOTAS).orderBy("numero").get())
                .map(doc -> toCuotaCuenta(doc.toObject(CuotaDocument.class)));
    }

    @Override
    public Mono<Void> actualizarCuota(String cuentaId, String cuotaId, Map<String, Object> campos) {
        return toMono(db.collection(COL_CUENTAS).document(cuentaId)
                .collection(COL_CUOTAS).document(cuotaId).update(campos)).then();
    }

    @Override
    public Mono<Void> actualizarCuenta(String cuentaId, Map<String, Object> campos) {
        return toMono(db.collection(COL_CUENTAS).document(cuentaId).update(campos)).then();
    }

    // ── Mappers ───────────────────────────────────────────────────────────

    private Mono<CuentaPorPagar> enrichWithCuotas(CuentaDocument doc) {
        return toFlux(db.collection(COL_CUENTAS).document(doc.getId())
                .collection(COL_CUOTAS).orderBy("numero").get())
                .map(snap -> toCuotaCuenta(snap.toObject(CuotaDocument.class)))
                .collectList()
                .map(cuotas -> toCuentaPorPagar(doc, cuotas));
    }

    private CuentaPorPagar toCuentaPorPagar(CuentaDocument doc, List<CuotaCuenta> cuotas) {
        return CuentaPorPagar.builder()
                .id(doc.getId())
                .tipo(doc.getTipo() != null ? TipoCuenta.valueOf(doc.getTipo()) : null)
                .proveedor(doc.getProveedor())
                .descripcion(doc.getDescripcion())
                .numeroDocumento(doc.getNumeroDocumento())
                .montoTotal(doc.getMontoTotal() != null ? BigDecimal.valueOf(doc.getMontoTotal()) : BigDecimal.ZERO)
                .numeroCuotas(doc.getNumeroCuotas() != null ? doc.getNumeroCuotas() : 0)
                .estado(doc.getEstado() != null ? EstadoCuenta.valueOf(doc.getEstado()) : EstadoCuenta.PENDIENTE)
                .fechaVencimiento(doc.getFechaVencimiento() != null ? LocalDate.parse(doc.getFechaVencimiento()) : null)
                .creadoEn(doc.getCreadoEn() != null ? LocalDateTime.parse(doc.getCreadoEn()) : null)
                .cuotas(cuotas)
                .build();
    }

    private CuotaCuenta toCuotaCuenta(CuotaDocument doc) {
        return CuotaCuenta.builder()
                .id(doc.getId())
                .cuentaId(doc.getCuentaId())
                .numero(doc.getNumero() != null ? doc.getNumero() : 0)
                .monto(doc.getMonto() != null ? BigDecimal.valueOf(doc.getMonto()) : BigDecimal.ZERO)
                .fechaVencimiento(doc.getFechaVencimiento() != null ? LocalDate.parse(doc.getFechaVencimiento()) : null)
                .fechaPago(doc.getFechaPago() != null ? LocalDate.parse(doc.getFechaPago()) : null)
                .estado(doc.getEstado() != null ? EstadoCuenta.valueOf(doc.getEstado()) : EstadoCuenta.PENDIENTE)
                .build();
    }

    private Map<String, Object> toCuentaDocument(CuentaPorPagar c) {
        return Map.of(
                "id", c.getId(),
                "tipo", c.getTipo().name(),
                "proveedor", c.getProveedor(),
                "descripcion", c.getDescripcion(),
                "numeroDocumento", c.getNumeroDocumento(),
                "montoTotal", c.getMontoTotal().doubleValue(),
                "numeroCuotas", c.getNumeroCuotas(),
                "estado", c.getEstado().name(),
                "fechaVencimiento", c.getFechaVencimiento().toString(),
                "creadoEn", c.getCreadoEn().toString()
        );
    }

    private Map<String, Object> toCuotaDocument(CuotaCuenta cuota, CuentaPorPagar cuenta) {
        return Map.of(
                "id", cuota.getId(),
                "cuentaId", cuota.getCuentaId(),
                "numero", cuota.getNumero(),
                "monto", cuota.getMonto().doubleValue(),
                "fechaVencimiento", cuota.getFechaVencimiento().toString(),
                "estado", cuota.getEstado().name(),
                "proveedor", cuenta.getProveedor(),
                "descripcion", cuenta.getDescripcion(),
                "tipo", cuenta.getTipo().name()
        );
    }
}
