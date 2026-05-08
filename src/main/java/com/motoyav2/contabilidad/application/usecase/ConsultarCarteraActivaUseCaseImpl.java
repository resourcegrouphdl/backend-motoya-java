package com.motoyav2.contabilidad.application.usecase;

import com.motoyav2.contabilidad.domain.model.SnapshotCartera;
import com.motoyav2.contabilidad.domain.port.in.ConsultarCarteraActivaUseCase;
import com.motoyav2.contabilidad.domain.port.out.CarteraPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultarCarteraActivaUseCaseImpl implements ConsultarCarteraActivaUseCase {

    private final CarteraPort carteraPort;

    @Override
    public Mono<SnapshotCartera> ejecutar(String tiendaId) {
        log.debug("Consultando cartera activa tiendaId={}", tiendaId);

        return carteraPort.findAllCasos(tiendaId)
                .collectList()
                .map(snapshots -> {
                    int totalContratos        = snapshots.size();
                    double capitalOriginal    = 0.0;
                    double saldoPendiente     = 0.0;
                    double totalPagado        = 0.0;
                    double totalMora          = 0.0;

                    for (var s : snapshots) {
                        capitalOriginal += s.getCapitalOriginalTotal() != null ? s.getCapitalOriginalTotal() : 0.0;
                        saldoPendiente  += s.getSaldoPendienteTotal()  != null ? s.getSaldoPendienteTotal()  : 0.0;
                        totalPagado     += s.getTotalPagado()          != null ? s.getTotalPagado()          : 0.0;
                        totalMora       += s.getTotalMora()            != null ? s.getTotalMora()            : 0.0;
                    }

                    double porcentajeRecuperacion = capitalOriginal > 0
                            ? (totalPagado / capitalOriginal) * 100.0
                            : 0.0;

                    return SnapshotCartera.builder()
                            .totalContratos(totalContratos)
                            .capitalOriginalTotal(capitalOriginal)
                            .saldoPendienteTotal(saldoPendiente)
                            .totalPagado(totalPagado)
                            .totalMora(totalMora)
                            .porcentajeRecuperacion(porcentajeRecuperacion)
                            .fechaCorte(LocalDate.now())
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Error consultando cartera activa: {}", e.getMessage(), e);
                    return Mono.just(SnapshotCartera.builder()
                            .totalContratos(0)
                            .capitalOriginalTotal(0.0).saldoPendienteTotal(0.0)
                            .totalPagado(0.0).totalMora(0.0)
                            .porcentajeRecuperacion(0.0)
                            .fechaCorte(LocalDate.now())
                            .build());
                });
    }
}
