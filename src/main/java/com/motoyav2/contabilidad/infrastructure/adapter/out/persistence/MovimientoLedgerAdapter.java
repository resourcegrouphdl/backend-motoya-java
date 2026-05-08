package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.motoyav2.contabilidad.domain.model.LineaIngreso;
import com.motoyav2.contabilidad.domain.port.out.MovimientoLedgerPort;
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
public class MovimientoLedgerAdapter implements MovimientoLedgerPort {

    private static final String COL  = "cobranzas-movimientos";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final Firestore firestore;

    @Override
    public Flux<LineaIngreso> findPagosByPeriodo(LocalDate desde, LocalDate hasta, String tiendaId) {
        Date inicio = Date.from(desde.atStartOfDay(LIMA).toInstant());
        Date fin    = Date.from(hasta.plusDays(1).atStartOfDay(LIMA).toInstant());

        // Solo filtro por rango de fechas (índice single-field automático).
        // tipo y storeId se filtran en memoria para evitar índices compuestos.
        Query query = firestore.collection(COL)
                .whereGreaterThanOrEqualTo("creadoEn", inicio)
                .whereLessThan("creadoEn", fin);

        final Query finalQuery = query;
        return toMono(finalQuery.get())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .filter(doc -> "PAGO_CUOTA".equals(doc.getString("tipo")))
                .filter(doc -> tiendaId == null || tiendaId.isBlank()
                        || tiendaId.equals(doc.getString("storeId")))
                .map(doc -> {
                    LocalDate fecha = null;
                    Date creadoEnDate = doc.getDate("creadoEn");
                    if (creadoEnDate != null) {
                        fecha = creadoEnDate.toInstant().atZone(LIMA).toLocalDate();
                    }

                    // monto en movimientos es NEGATIVO (abono) → Math.abs para visualización
                    Double montoRaw = doc.getDouble("monto");
                    double monto = montoRaw != null ? Math.abs(montoRaw) : 0.0;

                    return LineaIngreso.builder()
                            .id(doc.getId())
                            .contratoId(doc.getString("contratoId")   != null ? doc.getString("contratoId")   : "")
                            .monto(monto)
                            .fecha(fecha)
                            .tipo(doc.getString("tipo")               != null ? doc.getString("tipo")         : "PAGO_CUOTA")
                            .voucherId(doc.getString("voucherId")     != null ? doc.getString("voucherId")    : "")
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error leyendo cobranzas-movimientos: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }
}
