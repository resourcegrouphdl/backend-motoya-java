package com.motoyav2.cobranza.infrastructure.scheduler;

import com.motoyav2.cobranza.application.service.EstrategiaAutomaticaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * Scheduler automático de estrategias de cobranza.
 *
 * Se ejecuta dos veces al día en horario operativo:
 *  - 09:00 Lima — turno mañana
 *  - 14:00 Lima — turno tarde
 *
 * La lógica de negocio (filtros de mora, frecuencia, horario de estrategia,
 * envío WA, registro de eventos) vive en {@link EstrategiaAutomaticaService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CobranzaEstrategiaScheduler {

    private final EstrategiaAutomaticaService estrategiaAutomaticaService;

    @Scheduled(cron = "0 0 9 * * MON-SAT", zone = "America/Lima")
    public void turnoManana() {
        log.info("[SCHEDULER-ESTRATEGIA] Turno mañana — evaluando estrategias");
        ejecutar();
    }

    @Scheduled(cron = "0 0 14 * * MON-SAT", zone = "America/Lima")
    public void turnoTarde() {
        log.info("[SCHEDULER-ESTRATEGIA] Turno tarde — evaluando estrategias");
        ejecutar();
    }

    private void ejecutar() {
        estrategiaAutomaticaService.ejecutarEstrategiasActivas()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        total -> log.info("[SCHEDULER-ESTRATEGIA] Ciclo completado — contactados: {}", total),
                        err   -> log.error("[SCHEDULER-ESTRATEGIA] Error: {}", err.getMessage())
                );
    }
}
