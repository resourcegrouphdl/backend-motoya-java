package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.motoyav2.contabilidad.domain.model.PagoTiendaData;
import com.motoyav2.contabilidad.domain.port.out.FacturaTiendaPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacturaTiendaFirestoreAdapter implements FacturaTiendaPort {

    private static final String COL  = "finanzas_facturas";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final Firestore firestore;

    @Override
    public Flux<PagoTiendaData> findTodosPagados() {
        return toMono(firestore.collection(COL).get())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .flatMap(this::extraerPagosPagados)
                .onErrorResume(e -> {
                    log.error("[CONTABILIDAD] Error leyendo facturas tienda: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Cada factura tiene una sub-colección 'pagos'. Leemos directamente del mapa
     * embebido si existe, o usamos la sub-colección si está separada.
     * La implementación lee el campo 'pagos' embebido en el documento de factura.
     */
    @SuppressWarnings("unchecked")
    private Flux<PagoTiendaData> extraerPagosPagados(QueryDocumentSnapshot facturaDoc) {
        String facturaId = facturaDoc.getId();
        String tiendaId  = str(facturaDoc.getString("tiendaId"));
        String contratoId = str(facturaDoc.getString("ventaId")); // referencia al contrato

        // Intentar leer pagos como sub-colección
        return toMono(firestore.collection(COL).document(facturaId).collection("pagos").get())
                .flatMapMany(pagosSnap -> Flux.fromIterable(pagosSnap.getDocuments()))
                .filter(pagoDoc -> "PAGADO".equals(pagoDoc.getString("estado")))
                .map(pagoDoc -> {
                    Double monto = pagoDoc.getDouble("monto");
                    String fechaPagoStr = pagoDoc.getString("fechaPago");
                    Date fechaPago = null;
                    if (fechaPagoStr != null) {
                        try {
                            LocalDate ld = LocalDate.parse(fechaPagoStr);
                            fechaPago = Date.from(ld.atStartOfDay(LIMA).toInstant());
                        } catch (Exception ignored) {}
                    }
                    if (fechaPago == null) {
                        Date alt = pagoDoc.getDate("fechaPago");
                        fechaPago = alt;
                    }

                    String ref = facturaId + "_" + pagoDoc.getId();
                    return new PagoTiendaData(
                            ref,
                            contratoId,
                            tiendaId,
                            monto != null ? monto : 0.0,
                            fechaPago != null ? fechaPago.toInstant() : null
                    );
                })
                .onErrorResume(e -> {
                    log.warn("[CONTABILIDAD] No se pudieron leer pagos de factura {}: {}", facturaId, e.getMessage());
                    return Flux.empty();
                });
    }

    private String str(String v) { return v != null ? v : ""; }
}
