package com.motoyav2.evaluacion.infrastructure.scheduler;

import com.motoyav2.evaluacion.domain.port.in.ArchivarSolicitudesAbandonadasUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler diario que archiva solicitudes abandonadas.
 * Corre cada día a las 02:00 AM (hora del servidor / UTC).
 * Configurable: evaluacion.cierre-automatico.dias-inactividad (default 15).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CierreAutomaticoScheduler {

    private final ArchivarSolicitudesAbandonadasUseCase archivarUseCase;

    @org.springframework.beans.factory.annotation.Value("${evaluacion.cierre-automatico.dias-inactividad:15}")
    private int diasInactividad;

    @Scheduled(cron = "0 0 2 * * *")
    public void ejecutar() {
        log.info("[SCHEDULER] Iniciando cierre automático de solicitudes abandonadas (>{}d)", diasInactividad);
        archivarUseCase.archivar(diasInactividad)
                .subscribe(
                        n -> log.info("[SCHEDULER] Cierre automático completado: {} solicitudes archivadas", n),
                        e -> log.error("[SCHEDULER] Error en cierre automático: {}", e.getMessage())
                );
    }
}
