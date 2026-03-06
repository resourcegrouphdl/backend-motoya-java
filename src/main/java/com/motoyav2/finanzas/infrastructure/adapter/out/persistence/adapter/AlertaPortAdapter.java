package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.motoyav2.finanzas.application.port.out.AlertaFinancieraPort;
import com.motoyav2.finanzas.domain.enums.ModuloAlerta;
import com.motoyav2.finanzas.domain.enums.TipoAlerta;
import com.motoyav2.finanzas.domain.model.AlertaFinanciera;
import com.motoyav2.finanzas.domain.model.DashboardFinanzas;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils.*;

@Component
@RequiredArgsConstructor
public class AlertaPortAdapter implements AlertaFinancieraPort {

    private final Firestore db;

    @Override
    public Flux<AlertaFinanciera> findAllAlertas() {
        return Flux.merge(alertasFacturas(), alertasCuentas())
                .sort(Comparator.comparingInt(a -> ordenAlerta(a.getTipo())));
    }

    @Override
    public Mono<List<DashboardFinanzas.ProximoPago>> findProximosPagos() {
        String hoy = LocalDate.now().toString();
        String en7Dias = LocalDate.now().plusDays(7).toString();

        Flux<DashboardFinanzas.ProximoPago> pagosPorVencer = toFlux(
                db.collectionGroup("pagos")
                        .whereGreaterThanOrEqualTo("fechaProgramada", hoy)
                        .whereLessThanOrEqualTo("fechaProgramada", en7Dias)
                        .get())
                .filter(doc -> !"PAGADO".equals(doc.getString("estado")))
                .map(doc -> toProximoPago(doc, "FACTURAS"));

        Flux<DashboardFinanzas.ProximoPago> cuotasPorVencer = toFlux(
                db.collectionGroup("cuotas")
                        .whereEqualTo("estado", "PENDIENTE")
                        .whereGreaterThanOrEqualTo("fechaVencimiento", hoy)
                        .whereLessThanOrEqualTo("fechaVencimiento", en7Dias)
                        .get())
                .map(doc -> toProximoPago(doc, "CUENTAS"));

        return Flux.merge(pagosPorVencer, cuotasPorVencer)
                .sort(Comparator.comparing(DashboardFinanzas.ProximoPago::getFechaVencimiento))
                .take(20)
                .collectList();
    }

    // ── Alertas de facturas (collectionGroup pagos) ───────────────────────

    private Flux<AlertaFinanciera> alertasFacturas() {
        String hoy = LocalDate.now().toString();
        String en7Dias = LocalDate.now().plusDays(7).toString();

        Flux<AlertaFinanciera> vencidos = toFlux(db.collectionGroup("pagos")
                .whereNotEqualTo("estado", "PAGADO")
                .whereLessThan("fechaProgramada", hoy).get())
                .map(doc -> mapPagoToAlerta(doc, TipoAlerta.VENCIDO));

        Flux<AlertaFinanciera> hoyAlertas = toFlux(db.collectionGroup("pagos")
                .whereNotEqualTo("estado", "PAGADO")
                .whereEqualTo("fechaProgramada", hoy).get())
                .map(doc -> mapPagoToAlerta(doc, TipoAlerta.HOY));

        Flux<AlertaFinanciera> proximos = toFlux(db.collectionGroup("pagos")
                .whereNotEqualTo("estado", "PAGADO")
                .whereGreaterThan("fechaProgramada", hoy)
                .whereLessThanOrEqualTo("fechaProgramada", en7Dias).get())
                .map(doc -> mapPagoToAlerta(doc, TipoAlerta.PROXIMO));

        return Flux.merge(vencidos, hoyAlertas, proximos);
    }

    // ── Alertas de cuentas (collectionGroup cuotas) ───────────────────────

    private Flux<AlertaFinanciera> alertasCuentas() {
        String hoy = LocalDate.now().toString();
        String en7Dias = LocalDate.now().plusDays(7).toString();

        Flux<AlertaFinanciera> vencidas = toFlux(db.collectionGroup("cuotas")
                .whereEqualTo("estado", "VENCIDO").get())
                .map(doc -> mapCuotaToAlerta(doc, TipoAlerta.VENCIDO));

        Flux<AlertaFinanciera> proximas = toFlux(db.collectionGroup("cuotas")
                .whereEqualTo("estado", "PENDIENTE")
                .whereLessThanOrEqualTo("fechaVencimiento", en7Dias).get())
                .map(doc -> {
                    String fecha = doc.getString("fechaVencimiento");
                    TipoAlerta tipo = hoy.equals(fecha) ? TipoAlerta.HOY : TipoAlerta.PROXIMO;
                    return mapCuotaToAlerta(doc, tipo);
                });

        return Flux.merge(vencidas, proximas);
    }

    // ── Mappers ───────────────────────────────────────────────────────────

    private AlertaFinanciera mapPagoToAlerta(DocumentSnapshot doc, TipoAlerta tipo) {
        String pagoId = doc.getId();
        String facturaId = doc.getString("facturaId");
        String tienda = doc.getString("tiendaNombre");
        String cliente = doc.getString("clienteNombre");
        Double monto = doc.getDouble("monto");
        return AlertaFinanciera.builder()
                .id("f-" + pagoId)
                .tipo(tipo)
                .mensaje(tienda + " — " + cliente)
                .monto(monto != null ? BigDecimal.valueOf(monto) : BigDecimal.ZERO)
                .referencia(facturaId)
                .modulo(ModuloAlerta.FACTURAS)
                .ruta("/finanzas/pagos-tiendas/" + facturaId)
                .build();
    }

    private AlertaFinanciera mapCuotaToAlerta(DocumentSnapshot doc, TipoAlerta tipo) {
        String cuotaId = doc.getId();
        String cuentaId = doc.getString("cuentaId");
        String proveedor = doc.getString("proveedor");
        String descripcion = doc.getString("descripcion");
        Double monto = doc.getDouble("monto");
        return AlertaFinanciera.builder()
                .id("c-" + cuotaId)
                .tipo(tipo)
                .mensaje(proveedor + " — " + descripcion)
                .monto(monto != null ? BigDecimal.valueOf(monto) : BigDecimal.ZERO)
                .referencia(cuentaId)
                .modulo(ModuloAlerta.CUENTAS)
                .ruta("/finanzas/cuentas-pagar")
                .build();
    }

    private DashboardFinanzas.ProximoPago toProximoPago(DocumentSnapshot doc, String modulo) {
        Double monto = doc.getDouble("monto");
        String fecha = "FACTURAS".equals(modulo)
                ? doc.getString("fechaProgramada")
                : doc.getString("fechaVencimiento");
        return DashboardFinanzas.ProximoPago.builder()
                .id(doc.getId())
                .descripcion(doc.getString("tiendaNombre") != null
                        ? doc.getString("tiendaNombre") + " — " + doc.getString("clienteNombre")
                        : doc.getString("proveedor") + " — " + doc.getString("descripcion"))
                .monto(monto != null ? BigDecimal.valueOf(monto) : BigDecimal.ZERO)
                .fechaVencimiento(fecha)
                .estado("PROXIMO")
                .modulo(modulo)
                .ruta("FACTURAS".equals(modulo)
                        ? "/finanzas/pagos-tiendas/" + doc.getString("facturaId")
                        : "/finanzas/cuentas-pagar")
                .build();
    }

    private int ordenAlerta(TipoAlerta tipo) {
        return switch (tipo) {
            case VENCIDO -> 0;
            case HOY     -> 1;
            case PROXIMO -> 2;
        };
    }
}
