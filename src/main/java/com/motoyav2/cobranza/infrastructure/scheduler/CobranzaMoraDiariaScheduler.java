package com.motoyav2.cobranza.infrastructure.scheduler;

import com.motoyav2.cobranza.application.service.MoraDiariaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * Job diario que procesa la mora de todos los casos activos de cobranza.
 * Se ejecuta a las 07:30 Lima (30 min después del recálculo de métricas de apertura).
 *
 * Qué hace en cada ejecución:
 *  - Marca cuotas como VENCIDA cuando su fecha de vencimiento ya pasó.
 *  - Calcula mora acumulada (+S/ 3.00 por día).
 *  - Escala nivelEstrategia según tramos de días.
 *  - Envía recordatorio WA cada 3 días si no hay promesa vigente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CobranzaMoraDiariaScheduler {

    private final MoraDiariaService moraDiariaService;

    @Scheduled(cron = "0 30 7 * * *", zone = "America/Lima")
    public void ejecutarMoraDiaria() {
        log.info("[SCHEDULER-MORA] Iniciando procesamiento diario de mora");
        moraDiariaService.procesarMoraDiaria()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        contratoId -> log.debug("[SCHEDULER-MORA] Caso procesado: {}", contratoId),
                        err -> log.error("[SCHEDULER-MORA] Error en lote: {}", err.getMessage()),
                        () -> log.info("[SCHEDULER-MORA] Procesamiento diario completado")
                );
    }
}
