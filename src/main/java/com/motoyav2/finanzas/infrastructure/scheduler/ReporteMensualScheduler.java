package com.motoyav2.finanzas.infrastructure.scheduler;

import com.motoyav2.finanzas.application.port.in.GenerarReporteMensualUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Genera automáticamente el reporte mensual de contabilidad el día 2 de cada mes a las 2 AM Lima,
 * asegurándose de que el mes anterior ya está cerrado (todos los pagos del día 1 ya se procesaron).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReporteMensualScheduler {

    private final GenerarReporteMensualUseCase generarReporte;

    @Scheduled(cron = "0 0 2 2 * *", zone = "America/Lima")
    public void generarReporteMesAnterior() {
        String mes = LocalDate.now().minusMonths(1).toString().substring(0, 7); // "2026-03"
        log.info("[ReporteMensual] Iniciando generación automática para mes={}", mes);
        generarReporte.ejecutar(mes)
                .subscribe(
                        url -> log.info("[ReporteMensual] Completado mes={} url={}", mes, url),
                        e   -> log.error("[ReporteMensual] Error mes={}: {}", mes, e.getMessage())
                );
    }
}
