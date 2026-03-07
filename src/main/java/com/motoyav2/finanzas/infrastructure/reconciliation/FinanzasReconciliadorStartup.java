package com.motoyav2.finanzas.infrastructure.reconciliation;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.FieldValue;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.document.ContratoDocument;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.document.FacturaVehiculoEmbedded;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.document.TiendaInfoEmbedded;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Reconciliador de arranque para el módulo Finanzas.
 *
 * Espera 15s para que la conexión TLS/gRPC de Firestore esté estable en Cloud Run,
 * luego reintenta hasta 5 veces con backoff exponencial (10s base, 60s máx).
 *
 * Solo ejecuta el barrido real la PRIMERA vez (flag en /finanzas_config/reconciliacion).
 * Arranques posteriores hacen 1 sola lectura y salen.
 *
 * Busca contratos con estado=FIRMADO y crea la factura en /facturas + subcollection /pagos
 * usando directamente los campos del documento (facturaVehiculo, tienda) con manejo de nulls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanzasReconciliadorStartup implements ApplicationRunner {

    private static final String COL_CONTRATOS  = "contratos";
    private static final String COL_FACTURAS   = "facturas";
    private static final String COL_PAGOS      = "pagos";
    private static final String COL_COMISIONES = "comisiones";
    private static final String COL_SOLICITUDES= "solicitudes";
    private static final String COL_USERS      = "users";
    private static final String COL_CONFIG     = "finanzas_config";
    private static final String DOC_FLAG       = "reconciliacion";
    private static final String CAMPO_OK       = "completado";
    private static final String CAMPO_FECHA    = "fechaEjecucion";
    private static final String CAMPO_TOTAL    = "totalProcesados";
    private static final int    CONDICION_PAGO_DIAS = 15;

    private final Firestore db;

    @Override
    public void run(ApplicationArguments args) {
        // Cloud Run: espera 15s para que el canal gRPC/TLS de Firestore se estabilice.
        // Luego reintenta hasta 5 veces con backoff exponencial (10s base, 60s máx).
        // Tiempo máximo total: 15 + 10 + 20 + 40 + 60 + 60 ≈ 205s antes de desistir.
        Mono.delay(Duration.ofSeconds(15))
                .then(verificarYEjecutar())
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(10))
                        .maxBackoff(Duration.ofSeconds(60))
                        .filter(e -> e.getMessage() != null && e.getMessage().contains("UNAVAILABLE"))
                        .doBeforeRetry(s -> log.warn(
                                "[Reconciliador] Reintentando ({}/5) — causa: {}",
                                s.totalRetries() + 1, s.failure().getMessage())))
                .subscribe(
                        null,
                        e -> log.error("[Reconciliador] Falló tras reintentos — reintentará en próximo arranque: {}", e.getMessage())
                );
    }

    // ── Flag check ────────────────────────────────────────────────────────

    private Mono<Void> verificarYEjecutar() {
        return FirestoreReactiveUtils.toMono(
                db.collection(COL_CONFIG).document(DOC_FLAG).get())
                .flatMap(snap -> {
                    if (snap.exists() && Boolean.TRUE.equals(snap.getBoolean(CAMPO_OK))) {
                        log.info("[Reconciliador] Ya ejecutado — omitiendo barrido");
                        return Mono.empty();
                    }
                    log.info("[Reconciliador] Iniciando barrido de contratos en estado FIRMADO...");
                    return ejecutarBarrido();
                });
    }

    // ── Barrido principal ─────────────────────────────────────────────────

    private Mono<Void> ejecutarBarrido() {
        return FirestoreReactiveUtils.toFlux(
                db.collection(COL_CONTRATOS)
                        .whereEqualTo("estado", "FIRMADO")
                        .get())
                .map(snap -> snap.toObject(ContratoDocument.class))
                .flatMap(doc -> procesarContrato(doc), 5) // max 5 en paralelo
                .reduce(0, Integer::sum)
                .flatMap(this::marcarComoCompletado)
                .then();
    }

    private Mono<Integer> procesarContrato(ContratoDocument doc) {
        String contratoId = doc.getId();

        return FirestoreReactiveUtils.toMono(
                db.collection(COL_FACTURAS).document(contratoId).get())
                .flatMap(facturaSnap -> {
                    if (facturaSnap.exists()) {
                        log.debug("[Reconciliador] Factura ya existe — contratoId={}", contratoId);
                        return Mono.just(0);
                    }
                    return crearFacturaDesdeDoc(doc)
                            .doOnSuccess(v -> log.info(
                                    "[Reconciliador] Factura creada — contratoId={} tienda={}",
                                    contratoId, tiendaNombre(doc)))
                            .onErrorResume(e -> {
                                log.error("[Reconciliador] Error en contratoId={}: {}", contratoId, e.getMessage());
                                return Mono.empty();
                            })
                            .thenReturn(1);
                });
    }

    // ── Construcción de la factura directamente desde ContratoDocument ────

    private Mono<Void> crearFacturaDesdeDoc(ContratoDocument doc) {
        String contratoId   = doc.getId();
        String ahora        = Instant.now().toString();
        String hoy          = LocalDate.now().toString();

        // ── Datos de tienda (de tienda embedded) ──────────────────────────
        TiendaInfoEmbedded tienda = doc.getTienda();
        String tiendaId     = tienda != null ? nvl(tienda.getTiendaId())    : "";
        String tiendaNombre = tienda != null ? nvl(tienda.getNombreTienda()) : "";

        // ── Datos de factura vehículo ─────────────────────────────────────
        FacturaVehiculoEmbedded fv = doc.getFacturaVehiculo();
        String numeroFactura = fv != null ? nvl(fv.getNumeroFactura()) : "";
        String marca         = fv != null ? nvl(fv.getMarcaVehiculo())  : "";
        String modelo        = fv != null ? nvl(fv.getModeloVehiculo()) : "";
        String motoModelo    = (marca + " " + modelo).trim();

        // ── Datos del titular ─────────────────────────────────────────────
        String clienteNombre = "";
        if (doc.getTitular() != null) {
            clienteNombre = (nvl(doc.getTitular().getApellidos()) + " "
                    + nvl(doc.getTitular().getNombres())).trim();
        }

        // ── Monto total desde datos financieros ───────────────────────────
        double montoTotal = 0.0;
        if (doc.getDatosFinancieros() != null && doc.getDatosFinancieros().getPrecioVehiculo() != null) {
            montoTotal = doc.getDatosFinancieros().getPrecioVehiculo();
        }

        // ── Cálculo P1 (20%) y P2 (80%) ──────────────────────────────────
        BigDecimal total  = BigDecimal.valueOf(montoTotal);
        BigDecimal montoP1 = total.multiply(BigDecimal.valueOf(0.20)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoP2 = total.subtract(montoP1);

        String fechaP1 = LocalDate.now().plusDays(2).toString();
        String fechaP2 = LocalDate.now().plusDays(CONDICION_PAGO_DIAS).toString();

        // ── Documento factura ─────────────────────────────────────────────
        Map<String, Object> factura = new HashMap<>();
        factura.put("id",             contratoId);
        factura.put("numero",         numeroFactura);
        factura.put("tiendaId",       tiendaId);
        factura.put("tiendaNombre",   tiendaNombre);
        factura.put("ventaId",        nvl(doc.getEvaluacionId()));
        factura.put("clienteNombre",  clienteNombre);
        factura.put("motoModelo",     motoModelo);
        factura.put("montoTotal",     montoTotal);
        factura.put("fechaFactura",   hoy);
        factura.put("condicionPago",  CONDICION_PAGO_DIAS);
        factura.put("estado",         "PENDIENTE");
        factura.put("creadoEn",       ahora);
        factura.put("actualizadoEn",  ahora);
        factura.put("_alertaActiva",  true);
        factura.put("_tieneVencidos", false);

        // ── Pago 1: INICIAL ───────────────────────────────────────────────
        String pagoId1 = contratoId + "-P1";
        Map<String, Object> pago1 = new HashMap<>();
        pago1.put("id",              pagoId1);
        pago1.put("facturaId",       contratoId);
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
        String pagoId2 = contratoId + "-P2";
        Map<String, Object> pago2 = new HashMap<>();
        pago2.put("id",              pagoId2);
        pago2.put("facturaId",       contratoId);
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

        // ── WriteBatch atómico ────────────────────────────────────────────
        WriteBatch batch = db.batch();
        var facturaRef = db.collection(COL_FACTURAS).document(contratoId);
        batch.set(facturaRef, factura);
        batch.set(facturaRef.collection(COL_PAGOS).document(pagoId1), pago1);
        batch.set(facturaRef.collection(COL_PAGOS).document(pagoId2), pago2);
        batch.update(
                db.collection("finanzas_kpis").document("current"),
                "totalFacturasPendientes", FieldValue.increment(1),
                "ultimaActualizacion", ahora
        );

        return FirestoreReactiveUtils.toMono(batch.commit())
                .then(crearComisionDesdeDoc(doc));
    }

    // ── Comisión: evaluacionId → solicitudes → vendedorId → users ────────

    private Mono<Void> crearComisionDesdeDoc(ContratoDocument doc) {
        String evaluacionId = doc.getEvaluacionId();
        if (evaluacionId == null || evaluacionId.isBlank()) {
            log.debug("[Reconciliador] contratoId={} sin evaluacionId — omitiendo comisión", doc.getId());
            return Mono.empty();
        }

        String comisionId = doc.getId() + "-COM";
        return FirestoreReactiveUtils.toMono(
                db.collection(COL_COMISIONES).document(comisionId).get())
                .flatMap(comisionSnap -> {
                    if (comisionSnap.exists()) {
                        log.debug("[Reconciliador] Comisión ya existe — contratoId={}", doc.getId());
                        return Mono.empty();
                    }
                    return resolverVendedorDesdeDoc(evaluacionId)
                            .flatMap(vendedor -> guardarComisionDesdeDoc(comisionId, doc, vendedor))
                            .onErrorResume(e -> {
                                log.error("[Reconciliador] Error comisión contratoId={}: {}", doc.getId(), e.getMessage());
                                return Mono.empty();
                            });
                });
    }

    private Mono<Map<String, Object>> resolverVendedorDesdeDoc(String evaluacionId) {
        return FirestoreReactiveUtils.toMono(
                db.collection(COL_SOLICITUDES).document(evaluacionId).get())
                .flatMap(solicitudSnap -> {
                    if (!solicitudSnap.exists()) return Mono.empty();
                    String vendedorId = solicitudSnap.getString("vendedorId");
                    if (vendedorId == null || vendedorId.isBlank()) return Mono.empty();
                    return FirestoreReactiveUtils.toMono(
                            db.collection(COL_USERS).document(vendedorId).get())
                            .map(userSnap -> {
                                Map<String, Object> v = new HashMap<>();
                                v.put("vendedorId",    vendedorId);
                                v.put("firstName",     nvl(userSnap.getString("firstName")));
                                v.put("lastName",      nvl(userSnap.getString("lastName")));
                                v.put("email",         nvl(userSnap.getString("email")));
                                v.put("phone",         nvl(userSnap.getString("phone")));
                                v.put("documentNumber",nvl(userSnap.getString("documentNumber")));
                                v.put("documentType",  nvl(userSnap.getString("documentType")));
                                v.put("userType",      nvl(userSnap.getString("userType")));
                                return v;
                            });
                });
    }

    private Mono<Void> guardarComisionDesdeDoc(String comisionId, ContratoDocument doc, Map<String, Object> vendedor) {
        String ahora        = Instant.now().toString();
        String hoy          = LocalDate.now().toString();
        String vendedorId   = (String) vendedor.get("vendedorId");
        String nombre       = (vendedor.get("lastName") + " " + vendedor.get("firstName")).trim();

        TiendaInfoEmbedded tienda = doc.getTienda();
        String tiendaId     = tienda != null ? nvl(tienda.getTiendaId())    : "";
        String tiendaNombre = tienda != null ? nvl(tienda.getNombreTienda()) : "";

        Map<String, Object> comision = new HashMap<>();
        comision.put("id",                    comisionId);
        comision.put("contratoId",            doc.getId());
        comision.put("vendedorId",            vendedorId);
        comision.put("vendedorNombre",        nombre);
        comision.put("vendedorEmail",         vendedor.get("email"));
        comision.put("vendedorPhone",         vendedor.get("phone"));
        comision.put("vendedorDocumento",     vendedor.get("documentNumber"));
        comision.put("vendedorTipoDocumento", vendedor.get("documentType"));
        comision.put("vendedorUserType",      vendedor.get("userType"));
        comision.put("tiendaId",              tiendaId);
        comision.put("tiendaNombre",          tiendaNombre);
        comision.put("periodoInicio",         hoy);
        comision.put("periodoFin",            hoy);
        comision.put("totalVentas",           1);
        comision.put("montoComision",         0.0);
        comision.put("estado",                "PENDIENTE");
        comision.put("pagadoEn",              null);
        comision.put("creadoEn",              ahora);
        comision.put("actualizadoEn",         ahora);

        return FirestoreReactiveUtils.toMono(
                db.collection(COL_COMISIONES).document(comisionId).set(comision))
                .doOnSuccess(v -> log.info(
                        "[Reconciliador] Comisión creada — id={} vendedor={} tienda={}",
                        comisionId, nombre, tiendaNombre))
                .then();
    }

    // ── Marcar flag ───────────────────────────────────────────────────────

    private Mono<Void> marcarComoCompletado(int totalCreadas) {
        log.info("[Reconciliador] Completado — facturas nuevas creadas: {}", totalCreadas);
        return FirestoreReactiveUtils.toMono(
                db.collection(COL_CONFIG).document(DOC_FLAG).set(Map.of(
                        CAMPO_OK,    true,
                        CAMPO_FECHA, Instant.now().toString(),
                        CAMPO_TOTAL, totalCreadas
                ))
        ).then();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String nvl(String val) {
        return val != null ? val : "";
    }

    private String tiendaNombre(ContratoDocument doc) {
        return doc.getTienda() != null ? nvl(doc.getTienda().getNombreTienda()) : "";
    }
}
