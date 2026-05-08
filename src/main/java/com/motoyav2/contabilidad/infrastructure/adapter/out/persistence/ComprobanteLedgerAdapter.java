package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.motoyav2.contabilidad.domain.model.ComprobanteContable;
import com.motoyav2.contabilidad.domain.port.out.ComprobanteLedgerPort;
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
public class ComprobanteLedgerAdapter implements ComprobanteLedgerPort {

    private static final String COL      = "cobranzas-comprobantes";
    private static final ZoneId LIMA     = ZoneId.of("America/Lima");

    private final Firestore firestore;

    @Override
    public Flux<ComprobanteContable> findByPeriodo(LocalDate desde, LocalDate hasta, String tiendaId, String tipo) {
        Date inicio = Date.from(desde.atStartOfDay(LIMA).toInstant());
        Date fin    = Date.from(hasta.plusDays(1).atStartOfDay(LIMA).toInstant());

        Query query = firestore.collection(COL)
                .whereGreaterThanOrEqualTo("creadoEn", inicio)
                .whereLessThan("creadoEn", fin);

        if (tiendaId != null && !tiendaId.isBlank()) {
            query = query.whereEqualTo("storeId", tiendaId);
        }

        if (tipo != null && !tipo.isBlank()) {
            query = query.whereEqualTo("tipo", tipo.toUpperCase());
        }

        final Query finalQuery = query;
        return toMono(finalQuery.get())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .map(doc -> {
                    LocalDate fechaEmision = null;
                    String fechaEmisionStr = doc.getString("fechaEmision");
                    if (fechaEmisionStr != null && !fechaEmisionStr.isBlank()) {
                        try {
                            fechaEmision = LocalDate.parse(fechaEmisionStr.substring(0, 10));
                        } catch (Exception e) {
                            log.warn("No se pudo parsear fechaEmision '{}' en doc {}", fechaEmisionStr, doc.getId());
                        }
                    }

                    LocalDate creadoEnLocal = null;
                    Date creadoEnDate = doc.getDate("creadoEn");
                    if (creadoEnDate != null) {
                        creadoEnLocal = creadoEnDate.toInstant().atZone(LIMA).toLocalDate();
                    }

                    // Receptor puede ser un mapa anidado
                    String receptorNombre    = "";
                    String receptorDocumento = "";
                    Object receptorObj = doc.get("receptor");
                    if (receptorObj instanceof java.util.Map<?, ?> receptorMap) {
                        Object nombre = receptorMap.get("nombreCompleto");
                        Object docNum = receptorMap.get("numeroDocumento");
                        receptorNombre    = nombre  != null ? nombre.toString()  : "";
                        receptorDocumento = docNum  != null ? docNum.toString()  : "";
                    }

                    return ComprobanteContable.builder()
                            .id(doc.getId())
                            .serie(doc.getString("serie")           != null ? doc.getString("serie")           : "")
                            .numero(doc.getString("numero")         != null ? doc.getString("numero")          : "")
                            .numeroCompleto(doc.getString("numeroCompleto") != null ? doc.getString("numeroCompleto") : "")
                            .tipo(doc.getString("tipo")             != null ? doc.getString("tipo")            : "")
                            .estado(doc.getString("estado")         != null ? doc.getString("estado")          : "")
                            .contratoId(doc.getString("contratoId") != null ? doc.getString("contratoId")      : "")
                            .storeId(doc.getString("storeId")       != null ? doc.getString("storeId")         : "")
                            .receptorNombre(receptorNombre)
                            .receptorDocumento(receptorDocumento)
                            .subTotal(doc.getDouble("subTotal")     != null ? doc.getDouble("subTotal")        : 0.0)
                            .igv(doc.getDouble("igv")               != null ? doc.getDouble("igv")             : 0.0)
                            .total(doc.getDouble("total")           != null ? doc.getDouble("total")           : 0.0)
                            .fechaEmision(fechaEmision)
                            .creadoEn(creadoEnLocal)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error leyendo cobranzas-comprobantes: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }
}
