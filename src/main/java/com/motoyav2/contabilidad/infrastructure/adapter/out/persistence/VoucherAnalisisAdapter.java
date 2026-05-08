package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.motoyav2.contabilidad.domain.model.ConcentracionBancaria;
import com.motoyav2.contabilidad.domain.model.DiscrepanciaVoucher;
import com.motoyav2.contabilidad.domain.port.out.VoucherAnalisisPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;


import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherAnalisisAdapter implements VoucherAnalisisPort {

    private static final String COL            = "cobranzas-vouchers";
    private static final ZoneId LIMA           = ZoneId.of("America/Lima");
    private static final double UMBRAL_DIFF    = 0.50;

    private final Firestore firestore;

    @Override
    public Flux<DiscrepanciaVoucher> findDiscrepancias(LocalDate desde, LocalDate hasta, String tiendaId) {
        return cargarAprobados(desde, hasta, tiendaId)
                .filter(doc -> {
                    Double detectado = getDouble(doc, "montoDetectado");
                    Double esperado  = getDouble(doc, "montoEsperado");
                    return Math.abs(detectado - esperado) > UMBRAL_DIFF;
                })
                .map(doc -> {
                    Double detectado = getDouble(doc, "montoDetectado");
                    Double esperado  = getDouble(doc, "montoEsperado");

                    LocalDate fecha = null;
                    java.util.Date creadoEnDate = doc.getDate("creadoEn");
                    if (creadoEnDate != null) {
                        fecha = creadoEnDate.toInstant().atZone(LIMA).toLocalDate();
                    }

                    // Extraer banco y confianza desde ocrResultado (mapa anidado)
                    String banco    = "";
                    double confianza = 0.0;
                    Object ocrObj = doc.get("ocrResultado");
                    if (ocrObj instanceof Map<?, ?> ocr) {
                        Object bancoObj    = ocr.get("banco");
                        Object confianzaObj = ocr.get("confianza");
                        banco    = bancoObj    != null ? bancoObj.toString()    : "";
                        confianza = confianzaObj instanceof Number n ? n.doubleValue() : 0.0;
                    }

                    // clienteNombre puede ser un mapa o un String
                    String clienteNombre = "";
                    Object clienteObj = doc.get("cliente");
                    if (clienteObj instanceof String s) {
                        clienteNombre = s;
                    } else if (clienteObj instanceof Map<?, ?> m) {
                        Object nombreObj = m.get("nombre");
                        clienteNombre = nombreObj != null ? nombreObj.toString() : "";
                    }

                    return DiscrepanciaVoucher.builder()
                            .voucherId(doc.getId())
                            .contratoId(doc.getString("contratoId") != null ? doc.getString("contratoId") : "")
                            .clienteNombre(clienteNombre)
                            .montoDetectado(detectado)
                            .montoEsperado(esperado)
                            .diferencia(detectado - esperado)
                            .confianzaOcr(confianza)
                            .banco(banco)
                            .estado(doc.getString("estado") != null ? doc.getString("estado") : "")
                            .fecha(fecha)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error consultando discrepancias: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }

    @Override
    public Flux<ConcentracionBancaria> findConcentracionBancaria(LocalDate desde, LocalDate hasta, String tiendaId) {
        return cargarAprobados(desde, hasta, tiendaId)
                .collectList()
                .flatMapMany(docs -> {
                    Map<String, Integer> conteos = new LinkedHashMap<>();
                    Map<String, Double>  montos  = new LinkedHashMap<>();
                    double totalMonto = 0.0;

                    for (var doc : docs) {
                        String banco = "";
                        Object ocrObj = doc.get("ocrResultado");
                        if (ocrObj instanceof Map<?, ?> ocr) {
                            Object bancoObj = ocr.get("banco");
                            banco = bancoObj != null && !bancoObj.toString().isBlank()
                                    ? bancoObj.toString() : "DESCONOCIDO";
                        } else {
                            banco = "DESCONOCIDO";
                        }

                        Double monto = getDouble(doc, "montoDetectado");
                        conteos.merge(banco, 1, Integer::sum);
                        montos.merge(banco, monto, Double::sum);
                        totalMonto += monto;
                    }

                    final double total = totalMonto;
                    return Flux.fromIterable(conteos.entrySet())
                            .map(entry -> ConcentracionBancaria.builder()
                                    .banco(entry.getKey())
                                    .cantidadOperaciones(entry.getValue())
                                    .montoTotal(montos.getOrDefault(entry.getKey(), 0.0))
                                    .porcentaje(total > 0
                                            ? (montos.getOrDefault(entry.getKey(), 0.0) / total) * 100.0
                                            : 0.0)
                                    .build())
                            .sort(Comparator.comparingDouble(ConcentracionBancaria::getMontoTotal).reversed());
                })
                .onErrorResume(e -> {
                    log.error("Error consultando concentracion bancaria: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }

    // ---- helpers ----

    private Flux<QueryDocumentSnapshot> cargarAprobados(
            LocalDate desde, LocalDate hasta, String tiendaId) {

        java.util.Date inicio = java.util.Date.from(desde.atStartOfDay(LIMA).toInstant());
        java.util.Date fin    = java.util.Date.from(hasta.plusDays(1).atStartOfDay(LIMA).toInstant());

        // Solo filtro por rango de fechas (índice single-field automático).
        // Estado y storeId se filtran en memoria para evitar índices compuestos.
        Query query = firestore.collection(COL)
                .whereGreaterThanOrEqualTo("creadoEn", inicio)
                .whereLessThan("creadoEn", fin);

        return toMono(query.get())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .filter(doc -> "APROBADO".equals(doc.getString("estado")))
                .filter(doc -> tiendaId == null || tiendaId.isBlank()
                        || tiendaId.equals(doc.getString("storeId")));
    }

    private double getDouble(com.google.cloud.firestore.DocumentSnapshot doc, String campo) {
        Double v = doc.getDouble(campo);
        return v != null ? v : 0.0;
    }
}
