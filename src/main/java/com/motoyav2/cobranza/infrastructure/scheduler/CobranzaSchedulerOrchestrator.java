package com.motoyav2.cobranza.infrastructure.scheduler;

import com.motoyav2.cobranza.application.service.CobranzaRecordatorioService;
import com.motoyav2.cobranza.application.service.EstrategiaAutomaticaService;
import com.motoyav2.cobranza.application.service.MoraDiariaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * Punto de entrada único para todos los jobs automáticos de cobranza.
 *
 * Reemplaza CobranzaMoraDiariaScheduler + CobranzaEstrategiaScheduler
 * y elimina la dependencia del endpoint HTTP de Cloud Scheduler.
 *
 * Flujo diario:
 *
 *   07:15  faseMora()          → MoraDiariaService:           cálculo numérico puro
 *   07:30  faseRecordatorios() → CobranzaRecordatorioService: WA a clientes + ventana 360
 *   09:00  faseEstrategias()   → EstrategiaAutomaticaService: estrategias custom (Lun–Sáb)
 *   14:00  faseEstrategias()   → EstrategiaAutomaticaService: turno tarde         (Lun–Sáb)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CobranzaSchedulerOrchestrator {

    private final MoraDiariaService          moraDiariaService;
    private final CobranzaRecordatorioService recordatorioService;
    private final EstrategiaAutomaticaService estrategiaService;

    // ── Fase 1: cálculo de mora ───────────────────────────────────────────────

    @Scheduled(cron = "0 15 7 * * *", zone = "America/Lima")
    public void faseMora() {
        log.info("[ORQUESTADOR] ▶ Fase mora (07:15 Lima)");
        moraDiariaService.procesarMora()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        id  -> log.debug("[ORQUESTADOR] Mora procesada: {}", id),
                        err -> log.error("[ORQUESTADOR] Error fase mora: {}", err.getMessage()),
                        ()  -> log.info("[ORQUESTADOR] ✓ Fase mora completada")
                );
    }

    // ── Fase 2: recordatorios WA ──────────────────────────────────────────────

    @Scheduled(cron = "0 30 7 * * *", zone = "America/Lima")
    public void faseRecordatorios() {
        log.info("[ORQUESTADOR] ▶ Fase recordatorios (07:30 Lima)");
        recordatorioService.procesarRecordatorios()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        err -> log.error("[ORQUESTADOR] Error fase recordatorios: {}", err.getMessage()),
                        ()  -> log.info("[ORQUESTADOR] ✓ Fase recordatorios completada")
                );
    }

    // ── Fase 3: estrategias customizadas ─────────────────────────────────────

    @Scheduled(cron = "0 0 9 * * MON-SAT", zone = "America/Lima")
    public void faseEstrategiasMañana() {
        log.info("[ORQUESTADOR] ▶ Fase estrategias mañana (09:00 Lima)");
        ejecutarEstrategias();
    }

    @Scheduled(cron = "0 0 14 * * MON-SAT", zone = "America/Lima")
    public void faseEstrategiasTarde() {
        log.info("[ORQUESTADOR] ▶ Fase estrategias tarde (14:00 Lima)");
        ejecutarEstrategias();
    }

    private void ejecutarEstrategias() {
        estrategiaService.ejecutarEstrategiasActivas()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        total -> log.info("[ORQUESTADOR] ✓ Estrategias completadas — contactados: {}", total),
                        err   -> log.error("[ORQUESTADOR] Error fase estrategias: {}", err.getMessage())
                );
    }
}