package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.motoyav2.contabilidad.domain.model.VoucherAprobadoData;
import com.motoyav2.contabilidad.domain.port.out.VoucherPagoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Date;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherPagoFirestoreAdapter implements VoucherPagoPort {

    private static final String COL = "cobranzas-vouchers";
    private final Firestore firestore;

    @Override
    public Flux<VoucherAprobadoData> findTodosAprobados() {
        return toMono(firestore.collection(COL).get())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .filter(doc -> "APROBADO".equals(doc.getString("estado")))
                .map(doc -> {
                    Date creadoEnDate = doc.getDate("creadoEn");
                    Double monto = doc.getDouble("monto");
                    if (monto == null) {
                        // fallback: leer desde ocrResultado.monto
                        Object ocr = doc.get("ocrResultado");
                        if (ocr instanceof java.util.Map<?,?> m) {
                            Object montoObj = m.get("monto");
                            monto = montoObj instanceof Number n ? n.doubleValue() : 0.0;
                        } else {
                            monto = 0.0;
                        }
                    }
                    return new VoucherAprobadoData(
                            doc.getId(),
                            str(doc.getString("contratoId")),
                            str(doc.getString("storeId")),
                            monto,
                            creadoEnDate != null ? creadoEnDate.toInstant() : null
                    );
                })
                .filter(v -> v.contratoId() != null && !v.contratoId().isBlank())
                .onErrorResume(e -> {
                    log.error("[CONTABILIDAD] Error leyendo vouchers: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private String str(String v) { return v != null ? v : ""; }
}
