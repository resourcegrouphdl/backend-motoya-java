package com.motoyav2.contabilidad.application.scheduler;

import com.motoyav2.contabilidad.domain.port.in.SincronizarContabilidadUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduler del ledger contable: cada 6 horas sincroniza pagos de clientes,
 * pagos a tiendas y comisiones hacia las colecciones contabilidad_*.
 * No modifica ninguna colección existente — solo escribe en contabilidad_*.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContabilidadSincronizacionScheduler {

    private final SincronizarContabilidadUseCase sincronizarUseCase;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${contabilidad.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Scheduled(fixedDelayString = "${contabilidad.scheduler.delay-ms:21600000}")
    public void ejecutar() {
        if (!schedulerEnabled) {
            log.debug("[SYNC-CONTABILIDAD] Scheduler desactivado — omitiendo ciclo");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.debug("[SYNC-CONTABILIDAD] Ciclo ya en ejecución, omitiendo...");
            return;
        }

        log.info("[SYNC-CONTABILIDAD] Iniciando ciclo de sincronización (6h)...");

        sincronizarUseCase.sincronizarIncremental()
                .doOnSuccess(total ->
                        log.info("[SYNC-CONTABILIDAD] Ciclo completado | movimientos nuevos={}", total))
                .doOnError(e ->
                        log.error("[SYNC-CONTABILIDAD] Error en ciclo: {}", e.getMessage(), e))
                .doFinally(signal -> running.set(false))
                .subscribe();
    }
}
