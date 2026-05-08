package com.motoyav2.contabilidad.application.usecase;

import com.motoyav2.contabilidad.domain.model.BucketMora;
import com.motoyav2.contabilidad.domain.port.in.ConsultarAgingUseCase;
import com.motoyav2.contabilidad.domain.port.out.CarteraPort;
import com.motoyav2.contabilidad.domain.service.AgingCalculadora;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultarAgingUseCaseImpl implements ConsultarAgingUseCase {

    private static final List<String> ORDEN_TRAMOS = List.of("AL_DIA", "1_30", "31_60", "61_90", "MAS_90");

    private final CarteraPort carteraPort;
    private final AgingCalculadora agingCalculadora;

    @Override
    public Flux<BucketMora> ejecutar(LocalDate fechaCorte, String tiendaId) {
        log.debug("Consultando aging fechaCorte={} tiendaId={}", fechaCorte, tiendaId);

        LocalDate corte = fechaCorte != null ? fechaCorte : LocalDate.now();

        return carteraPort.findAllCasos(tiendaId)
                .collectList()
                .flatMapMany(snapshots -> {
                    // Cada snapshot representa un caso/contrato individual.
                    // El campo saldoPendienteTotal ya contiene el saldo del contrato individual.
                    // Necesitamos también el campo de fecha de primera cuota impaga —
                    // ese dato viene del adapter; en CarteraPort el SnapshotCartera individual
                    // tiene fechaCorte reusado como "fecha primer impago" (ver CarteraFirestoreAdapter).
                    // Los buckets se construyen aquí agrupando por tramo.

                    Map<String, Integer>  conteos = new LinkedHashMap<>();
                    Map<String, Double>   saldos  = new LinkedHashMap<>();
                    for (String t : ORDEN_TRAMOS) { conteos.put(t, 0); saldos.put(t, 0.0); }

                    double totalSaldo = 0.0;
                    for (var snap : snapshots) {
                        // fechaCorte en el snapshot individual viene del adapter como la
                        // fecha de vencimiento de la primera cuota impaga.
                        int diasMora = 0;
                        if (snap.getFechaCorte() != null && !snap.getFechaCorte().equals(LocalDate.now())) {
                            diasMora = (int) Math.max(0,
                                    java.time.temporal.ChronoUnit.DAYS.between(snap.getFechaCorte(), corte));
                        }
                        String tramo = agingCalculadora.clasificarTramo(diasMora);
                        double saldo = snap.getSaldoPendienteTotal() != null ? snap.getSaldoPendienteTotal() : 0.0;

                        conteos.merge(tramo, 1, Integer::sum);
                        saldos.merge(tramo, saldo, Double::sum);
                        totalSaldo += saldo;
                    }

                    final double total = totalSaldo;
                    return Flux.fromIterable(ORDEN_TRAMOS)
                            .map(tramo -> BucketMora.builder()
                                    .tramo(tramo)
                                    .label(agingCalculadora.labelTramo(tramo))
                                    .cantidadContratos(conteos.getOrDefault(tramo, 0))
                                    .montoSaldo(saldos.getOrDefault(tramo, 0.0))
                                    .porcentaje(total > 0
                                            ? (saldos.getOrDefault(tramo, 0.0) / total) * 100.0
                                            : 0.0)
                                    .build());
                })
                .onErrorResume(e -> {
                    log.error("Error calculando aging: {}", e.getMessage(), e);
                    return Flux.empty();
                });
    }
}
