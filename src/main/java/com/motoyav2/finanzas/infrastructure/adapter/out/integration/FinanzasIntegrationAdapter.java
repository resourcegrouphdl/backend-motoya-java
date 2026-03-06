package com.motoyav2.finanzas.infrastructure.adapter.out.integration;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.port.out.FinanzasIntegrationPort;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Adaptador que implementa FinanzasIntegrationPort (del módulo contrato)
 * y persiste directamente en Firestore las colecciones de finanzas.
 *
 * Se ejecuta en el mismo proceso (monolito modular):
 *   contrato.ConfirmarFirmaService
 *        → contrato.FinanzasIntegrationPort (interface)
 *        → finanzas.FinanzasIntegrationAdapter (implementación)
 *        → Firestore: /facturas/{id} + /facturas/{id}/pagos/{P1,P2}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanzasIntegrationAdapter implements FinanzasIntegrationPort {

    private static final String COL_FACTURAS     = "facturas";
    private static final String COL_PAGOS        = "pagos";
    private static final String COL_KPIS         = "finanzas_kpis";
    private static final int    CONDICION_PAGO_DEFAULT = 15;

    private final Firestore db;

    @Override
    public Mono<Void> iniciarFacturaDesdeContrato(Contrato contrato) {
        // Idempotencia: si ya existe la factura, no duplicar
        return FirestoreReactiveUtils.toMono(
                db.collection(COL_FACTURAS).document(contrato.id()).get())
                .flatMap(snap -> {
                    if (snap.exists()) {
                        log.warn("[Finanzas] Factura ya existe para contratoId={} — omitiendo", contrato.id());
                        return Mono.empty();
                    }
                    return crearFacturaConPagos(contrato);
                });
    }

    // ── Creación atómica via WriteBatch ───────────────────────────────────

    private Mono<Void> crearFacturaConPagos(Contrato contrato) {
        String facturaId = contrato.id();
        String hoy = LocalDate.now().toString();

        // ── Datos del contrato ────────────────────────────────────────────
        String tiendaId     = contrato.tienda() != null ? contrato.tienda().tiendaId()     : "";
        String tiendaNombre = contrato.tienda() != null ? contrato.tienda().nombreTienda() : "";
        String clienteNombre = buildNombreCliente(contrato);
        String motoModelo    = buildMotoModelo(contrato);
        String numeroFactura = contrato.facturaVehiculo() != null
                ? contrato.facturaVehiculo().numeroFactura() : "";
        String ventaId = contrato.evaluacionId() != null ? contrato.evaluacionId() : "";

        double montoTotal = contrato.datosFinancieros() != null
                && contrato.datosFinancieros().precioVehiculo() != null
                ? contrato.datosFinancieros().precioVehiculo().doubleValue() : 0.0;

        // ── Cálculo de pagos (regla de negocio) ───────────────────────────
        BigDecimal total = BigDecimal.valueOf(montoTotal);
        BigDecimal montoP1 = total.multiply(BigDecimal.valueOf(0.20))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoP2 = total.subtract(montoP1);

        // P1 INICIAL: fechaFactura + 2 días hábiles (simplificado = +2 días calendario)
        String fechaP1 = LocalDate.now().plusDays(2).toString();
        // P2 SALDO: fechaFactura + condición (15 días por defecto)
        String fechaP2 = LocalDate.now().plusDays(CONDICION_PAGO_DEFAULT).toString();

        String ahora = Instant.now().toString();

        // ── Documento factura ─────────────────────────────────────────────
        Map<String, Object> factura = new HashMap<>();
        factura.put("id",             facturaId);
        factura.put("numero",         numeroFactura);
        factura.put("tiendaId",       tiendaId);
        factura.put("tiendaNombre",   tiendaNombre);
        factura.put("ventaId",        ventaId);
        factura.put("clienteNombre",  clienteNombre);
        factura.put("motoModelo",     motoModelo);
        factura.put("montoTotal",     montoTotal);
        factura.put("fechaFactura",   hoy);
        factura.put("condicionPago",  CONDICION_PAGO_DEFAULT);
        factura.put("estado",         "PENDIENTE");
        factura.put("creadoEn",       ahora);
        factura.put("actualizadoEn",  ahora);
        factura.put("_alertaActiva",  true);
        factura.put("_tieneVencidos", false);

        // ── Pago 1: INICIAL ───────────────────────────────────────────────
        String pagoId1 = facturaId + "-P1";
        Map<String, Object> pago1 = new HashMap<>();
        pago1.put("id",              pagoId1);
        pago1.put("facturaId",       facturaId);
        pago1.put("numero",          1);
        pago1.put("concepto",        "INICIAL");
        pago1.put("monto",           montoP1.doubleValue());
        pago1.put("fechaProgramada", fechaP1);
        pago1.put("fechaPago",       null);
        pago1.put("estado",          "PENDIENTE");
        pago1.put("voucherUrl",      null);
        pago1.put("metodoPago",      null);
        pago1.put("tiendaId",        tiendaId);
        pago1.put("tiendaNombre",    tiendaNombre);
        pago1.put("clienteNombre",   clienteNombre);
        pago1.put("actualizadoEn",   ahora);

        // ── Pago 2: SALDO ─────────────────────────────────────────────────
        String pagoId2 = facturaId + "-P2";
        Map<String, Object> pago2 = new HashMap<>();
        pago2.put("id",              pagoId2);
        pago2.put("facturaId",       facturaId);
        pago2.put("numero",          2);
        pago2.put("concepto",        "SALDO");
        pago2.put("monto",           montoP2.doubleValue());
        pago2.put("fechaProgramada", fechaP2);
        pago2.put("fechaPago",       null);
        pago2.put("estado",          "PENDIENTE");
        pago2.put("voucherUrl",      null);
        pago2.put("metodoPago",      null);
        pago2.put("tiendaId",        tiendaId);
        pago2.put("tiendaNombre",    tiendaNombre);
        pago2.put("clienteNombre",   clienteNombre);
        pago2.put("actualizadoEn",   ahora);

        // ── WriteBatch atómico: factura + P1 + P2 + KPI ──────────────────
        WriteBatch batch = db.batch();

        var facturaRef = db.collection(COL_FACTURAS).document(facturaId);
        batch.set(facturaRef, factura);
        batch.set(facturaRef.collection(COL_PAGOS).document(pagoId1), pago1);
        batch.set(facturaRef.collection(COL_PAGOS).document(pagoId2), pago2);

        // Incrementar KPI de facturas pendientes
        batch.update(db.collection(COL_KPIS).document("current"),
                "totalFacturasPendientes", com.google.cloud.firestore.FieldValue.increment(1),
                "ultimaActualizacion", ahora);

        return FirestoreReactiveUtils.toMono(batch.commit())
                .doOnSuccess(v -> log.info(
                        "[Finanzas] Factura creada desde contrato — facturaId={} tienda={} monto={}",
                        facturaId, tiendaNombre, montoTotal))
                .doOnError(e -> log.error(
                        "[Finanzas] Error al crear factura desde contrato={}: {}",
                        facturaId, e.getMessage()))
                .then();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String buildNombreCliente(Contrato contrato) {
        if (contrato.titular() == null) return "";
        String nombres   = nvl(contrato.titular().nombres());
        String apellidos = nvl(contrato.titular().apellidos());
        return (apellidos + " " + nombres).trim();
    }

    private String buildMotoModelo(Contrato contrato) {
        if (contrato.facturaVehiculo() == null) return "";
        String marca  = nvl(contrato.facturaVehiculo().marcaVehiculo());
        String modelo = nvl(contrato.facturaVehiculo().modeloVehiculo());
        return (marca + " " + modelo).trim();
    }

    private String nvl(String val) {
        return val != null ? val : "";
    }
}
