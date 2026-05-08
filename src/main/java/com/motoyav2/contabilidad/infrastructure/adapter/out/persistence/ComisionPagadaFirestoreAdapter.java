package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.contabilidad.domain.model.ComisionPagadaData;
import com.motoyav2.contabilidad.domain.port.out.ComisionPagadaPort;
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
public class ComisionPagadaFirestoreAdapter implements ComisionPagadaPort {

    private static final String COL  = "pagos_comisiones_vendedor";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final Firestore firestore;

    @Override
    public Flux<ComisionPagadaData> findTodosPagadas() {
        return toMono(firestore.collection(COL).get())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .filter(doc -> "PAGADO".equals(doc.getString("estado")))
                .map(doc -> {
                    Double monto = doc.getDouble("montoTotal");
                    String fechaPagadoStr = doc.getString("pagadoEn");
                    Date fechaPago = null;
                    if (fechaPagadoStr != null) {
                        try {
                            LocalDate ld = LocalDate.parse(fechaPagadoStr.substring(0, 10));
                            fechaPago = Date.from(ld.atStartOfDay(LIMA).toInstant());
                        } catch (Exception ignored) {}
                    }
                    return new ComisionPagadaData(
                            doc.getId(),
                            str(doc.getString("tiendaId")),
                            monto != null ? monto : 0.0,
                            fechaPago != null ? fechaPago.toInstant() : null
                    );
                })
                .onErrorResume(e -> {
                    log.error("[CONTABILIDAD] Error leyendo comisiones: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private String str(String v) { return v != null ? v : ""; }
}
