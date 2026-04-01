package com.motoyav2.finanzas.infrastructure.scheduler;

import com.motoyav2.finanzas.application.port.in.GenerarPagosQuincenalesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler que genera los batches de pago de comisiones.
 * Ejecuta el día 1 y el día 15 de cada mes a la 1:00 AM Lima.
 *
 * Día 15 → agrupa comisiones del 1 al 14 del mes actual  (PRIMERA_QUINCENA)
 * Día  1 → agrupa comisiones del 16 al fin del mes anterior (SEGUNDA_QUINCENA)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuincenaComisionScheduler {

    private final GenerarPagosQuincenalesUseCase generarPagos;

    @Scheduled(cron = "0 0 1 1,15 * *", zone = "America/Lima")
    public void generarPagosQuincenales() {
        log.info("[QuincenaComision] Iniciando generación de pagos quincenales");
        generarPagos.ejecutar()
                .subscribe(
                        n  -> log.info("[QuincenaComision] Completado — batches creados: {}", n),
                        e  -> log.error("[QuincenaComision] Error: {}", e.getMessage())
                );
    }
}
