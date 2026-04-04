package com.motoyav2.finanzas.infrastructure.adapter.out.persistence.adapter;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.motoyav2.finanzas.domain.model.PagoComisionVendedor;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.PagoComisionDocument;
import com.motoyav2.finanzas.infrastructure.adapter.out.persistence.document.ReporteContabilidadDocument;
import com.motoyav2.finanzas.infrastructure.adapter.out.storage.FinanzasPdfStorageService;
import com.motoyav2.finanzas.infrastructure.pdf.FinanzasPdfRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.motoyav2.finanzas.infrastructure.adapter.out.persistence.util.FirestoreReactiveUtils.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReporteMensualPortAdapter {

    private static final String COL_REPORTES   = "reportes_contabilidad";
    private static final String COL_PAGOS_COM  = "pagos_comisiones_vendedor";
    private static final String COL_PAGOS_FAC  = "pagos";                      // pagos de facturas tienda
    private static final String COL_CXP        = "finanzas_cuentas_pagar";

    private final Firestore               db;
    private final FinanzasPdfRenderer     renderer;
    private final FinanzasPdfStorageService storageService;

    // ── Listar reportes ───────────────────────────────────────────────────────

    public Flux<ReporteContabilidadDocument> findAll() {
        return toFlux(db.collection(COL_REPORTES)
                .orderBy("mes", Query.Direction.DESCENDING)
                .limit(24)
                .get())
                .map(snap -> snap.toObject(ReporteContabilidadDocument.class));
    }

    // ── Generar reporte para un mes ───────────────────────────────────────────

    public Mono<String> generarReporteMes(String mes) {
        YearMonth ym      = YearMonth.parse(mes);
        String    desde   = ym.atDay(1).toString();
        String    hasta   = ym.atEndOfMonth().toString();
        String    ahora   = Instant.now().toString();
        String    docId   = mes;

        // 1. Pagos de comisión confirmados en el mes
        Mono<List<PagoComisionDocument>> pagosComMono = toFlux(
                db.collection(COL_PAGOS_COM)
                        .whereEqualTo("estado", "PAGADO")
                        .whereGreaterThanOrEqualTo("pagadoEn", desde + "T00:00:00Z")
                        .whereLessThanOrEqualTo("pagadoEn", hasta + "T23:59:59Z")
                        .get())
                .map(snap -> snap.toObject(PagoComisionDocument.class))
                .collectList();

        // 2. Pagos de facturas tienda confirmados en el mes
        Mono<List<Map<String, Object>>> pagosTiendaMono = toFlux(
                db.collection(COL_PAGOS_FAC)
                        .whereEqualTo("estado", "PAGADO")
                        .whereGreaterThanOrEqualTo("fechaPago", desde)
                        .whereLessThanOrEqualTo("fechaPago", hasta)
                        .get())
                .map(snap -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) snap.getData();
                    return m != null ? m : Collections.<String, Object>emptyMap();
                })
                .collectList();

        return Mono.zip(pagosComMono, pagosTiendaMono)
                .flatMap(tuple -> {
                    List<PagoComisionDocument> pagosCom   = tuple.getT1();
                    List<Map<String, Object>>  pagosTienda = tuple.getT2();

                    double totalCom     = pagosCom.stream()
                            .mapToDouble(p -> p.getMontoTotal() != null ? p.getMontoTotal() : 0.0).sum();
                    double totalTienda  = pagosTienda.stream()
                            .mapToDouble(m -> toDouble(m.get("monto"))).sum();
                    double totalEgresos = totalCom + totalTienda;

                    // Construir variables para el template PDF
                    Map<String, Object> vars = new LinkedHashMap<>();
                    vars.put("mes",           mes);
                    vars.put("mesNombre",     ym.getMonth().getDisplayName(
                            java.time.format.TextStyle.FULL,
                            new Locale("es", "PE")) + " " + ym.getYear());
                    vars.put("fechaEmision",  LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    vars.put("pagosCom",      pagosCom);
                    vars.put("pagosTienda",   pagosTienda);
                    vars.put("totalCom",      totalCom);
                    vars.put("totalTienda",   totalTienda);
                    vars.put("totalEgresos",  totalEgresos);

                    byte[] pdf = renderer.render("finanzas/reporte-mensual", vars);
                    String gcsPath = "finanzas/reportes-mensuales/" + mes + ".pdf";

                    return storageService.subirPdf(gcsPath, pdf)
                            .flatMap(url -> {
                                ReporteContabilidadDocument doc = new ReporteContabilidadDocument();
                                doc.setId(docId);
                                doc.setMes(mes);
                                doc.setAnio(String.valueOf(ym.getYear()));
                                doc.setMesNombre(vars.get("mesNombre").toString());
                                doc.setTotalPagosComisiones(totalCom);
                                doc.setCantidadPagosComisiones(pagosCom.size());
                                doc.setTotalPagosTiendas(totalTienda);
                                doc.setCantidadPagosTiendas(pagosTienda.size());
                                doc.setTotalCuotasCxP(0.0);
                                doc.setCantidadCuotasCxP(0);
                                doc.setTotalEgresos(totalEgresos);
                                doc.setReporteUrl(url);
                                doc.setEstado("GENERADO");
                                doc.setGeneradoEn(ahora);
                                doc.setActualizadoEn(ahora);

                                return toMono(db.collection(COL_REPORTES).document(docId)
                                        .set(toMap(doc)))
                                        .thenReturn(url);
                            });
                })
                .doOnSuccess(url -> log.info("[ReporteMensual] Generado mes={} url={}", mes, url))
                .onErrorResume(e -> {
                    log.error("[ReporteMensual] Error generando mes={}: {}", mes, e.getMessage());
                    return Mono.error(e);
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private Map<String, Object> toMap(ReporteContabilidadDocument doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                        doc.getId());
        m.put("mes",                       doc.getMes());
        m.put("anio",                      doc.getAnio());
        m.put("mesNombre",                 doc.getMesNombre());
        m.put("totalPagosComisiones",      doc.getTotalPagosComisiones());
        m.put("cantidadPagosComisiones",   doc.getCantidadPagosComisiones());
        m.put("totalPagosTiendas",         doc.getTotalPagosTiendas());
        m.put("cantidadPagosTiendas",      doc.getCantidadPagosTiendas());
        m.put("totalCuotasCxP",            doc.getTotalCuotasCxP() != null ? doc.getTotalCuotasCxP() : 0.0);
        m.put("cantidadCuotasCxP",         doc.getCantidadCuotasCxP());
        m.put("totalEgresos",              doc.getTotalEgresos());
        m.put("reporteUrl",                doc.getReporteUrl());
        m.put("estado",                    doc.getEstado());
        m.put("generadoEn",                doc.getGeneradoEn());
        m.put("actualizadoEn",             doc.getActualizadoEn());
        return m;
    }
}
