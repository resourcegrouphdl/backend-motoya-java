package com.motoyav2.contabilidad.infrastructure.adapter.out.persistence;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.motoyav2.contabilidad.domain.model.SnapshotCartera;
import com.motoyav2.contabilidad.domain.port.out.CarteraPort;
import com.motoyav2.contabilidad.domain.service.AgingCalculadora;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static com.motoyav2.evaluacion.infrastructure.adapter.out.persistence.util.FirestoreUtils.toMono;

@Slf4j
@Component
@RequiredArgsConstructor
public class CarteraFirestoreAdapter implements CarteraPort {

    private static final String COL  = "cobranzas-casos";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final Firestore firestore;
    private final AgingCalculadora agingCalculadora;

    @Override
    public Flux<SnapshotCartera> findAllCasos(String tiendaId) {
        Query query = firestore.collection(COL)
                .whereIn("cicloVida", List.of("ACTIVO", "PROMESA_VIGENTE", "ACUERDO_VIGENTE"));

        if (tiendaId != null && !tiendaId.isBlank()) {
            query = query.whereEqualTo("storeId", tiendaId);
        }

        final Query finalQuery = query;
        return toMono(finalQuery.get())
                .flatMapMany(snap -> Flux.fromIterable(snap.getDocuments()))
                .map(doc -> {
                    Object rawFecha = doc.get("fechaVencimientoPrimerCuotaImpaga");
                    LocalDate fechaImpaga = null;
                    if (rawFecha != null) {
                        int diasMora = agingCalculadora.calcularDiasMora(rawFecha, LocalDate.now());
                        if (diasMora > 0) {
                            fechaImpaga = LocalDate.now().minusDays(diasMora);
                        }
                    }

                    Double capitalOriginal = doc.getDouble("capitalOriginal") != null
                            ? doc.getDouble("capitalOriginal") : 0.0;
                    Double saldoActual     = doc.getDouble("saldoActual")     != null
                            ? doc.getDouble("saldoActual")     : 0.0;
                    Double totalPagado     = doc.getDouble("totalPagado")     != null
                            ? doc.getDouble("totalPagado")     : 0.0;
                    Double totalMora       = doc.getDouble("totalMora")       != null
                            ? doc.getDouble("totalMora")       : 0.0;

                    // Cada snapshot representa UN contrato individual.
                    // fechaCorte se repurposa para almacenar la fecha de primera cuota impaga
                    // (o LocalDate.now() si el contrato está al día).
                    return SnapshotCartera.builder()
                            .totalContratos(1)
                            .capitalOriginalTotal(capitalOriginal)
                            .saldoPendienteTotal(saldoActual)
                            .totalPagado(totalPagado)
                            .totalMora(totalMora)
                            .porcentajeRecuperacion(capitalOriginal > 0
                                    ? (totalPagado / capitalOriginal) * 100.0 : 0.0)
                            .fechaCorte(fechaImpaga != null ? fechaImpaga : LocalDate.now())
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error leyendo cobranzas-casos: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }
}
