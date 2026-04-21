package com.motoyav2.cobranza.infrastructure.scheduler;

import com.motoyav2.cobranza.application.service.RecalcularMetricasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * Actualiza los KPIs de cobranza cada 15 minutos y fuerza un
 * recálculo completo a las 07:00 AM Lima (hora operativa de apertura).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CobranzaMetricasScheduler {

    private final RecalcularMetricasService recalcularService;

    /** Recálculo cada 15 min durante horario operativo (07:00–21:00 Lima). */
    @Scheduled(cron = "0 0/15 7-21 * * *", zone = "America/Lima")
    public void recalcularPeriodico() {
        log.info("[SCHEDULER-METRICAS] Recalculando KPIs de cobranza (periódico)");
        recalcularService.recalcular()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        doc -> log.info("[SCHEDULER-METRICAS] KPIs actualizados. casosActivos={}",
                                doc.getCasosActivos()),
                        err -> log.error("[SCHEDULER-METRICAS] Error: {}", err.getMessage())
                );
    }

    /** Recálculo completo al inicio del día operativo. */
    @Scheduled(cron = "0 0 7 * * *", zone = "America/Lima")
    public void recalcularApertura() {
        log.info("[SCHEDULER-METRICAS] Recalculando KPIs de cobranza (apertura del día)");
        recalcularService.recalcular()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        doc -> log.info("[SCHEDULER-METRICAS] KPIs apertura completados"),
                        err -> log.error("[SCHEDULER-METRICAS] Error apertura: {}", err.getMessage())
                );
    }
}
