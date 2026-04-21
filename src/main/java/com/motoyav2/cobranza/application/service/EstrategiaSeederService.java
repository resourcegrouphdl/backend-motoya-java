package com.motoyav2.cobranza.application.service;

import com.motoyav2.cobranza.application.port.out.EstrategiaPort;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.EstrategiaDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Date;
import java.util.List;

/**
 * Siembra las 4 estrategias de cobranza por defecto si la colección está vacía.
 * Se ejecuta una única vez al arranque de la aplicación.
 *
 * Las estrategias respetan la regulación interna Motoya:
 *  - MORA_TEMPRANA : 1–15 días  → recordatorio cada 3 días
 *  - MORA_MEDIA    : 16–30 días → recordatorio cada 2 días
 *  - MORA_CRITICA  : 31–60 días → recordatorio diario
 *  - JUDICIAL      : 61+ días   → intervención manual (frecuencia alta para escalado)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EstrategiaSeederService implements ApplicationRunner {

    private final EstrategiaPort estrategiaPort;

    @Override
    public void run(ApplicationArguments args) {
        estrategiaPort.findAll()
                .hasElements()
                .flatMapMany(tieneEstrategias -> {
                    if (tieneEstrategias) {
                        log.info("[SEEDER-ESTRATEGIAS] Estrategias ya configuradas — omitiendo seed");
                        return Flux.empty();
                    }
                    log.info("[SEEDER-ESTRATEGIAS] Sin estrategias — sembrando 4 por defecto");
                    return Flux.fromIterable(estrategiasPorDefecto())
                            .flatMap(estrategiaPort::save);
                })
                .subscribe(
                        e -> log.info("[SEEDER-ESTRATEGIAS] Estrategia creada: {}", e.getNombre()),
                        err -> log.error("[SEEDER-ESTRATEGIAS] Error: {}", err.getMessage())
                );
    }

    private List<EstrategiaDocument> estrategiasPorDefecto() {
        Date ahora = new Date();
        return List.of(
                EstrategiaDocument.builder()
                        .id("est-mora-temprana-wa")
                        .nombre("WA Mora Temprana D+1 a D+15")
                        .nivel("MORA_TEMPRANA")
                        .canal("WHATSAPP")
                        .plantillaId(null)          // usa NotificationFacade.notificarCuotaVencida()
                        .mensaje("Recordatorio automático: cuota vencida con mora acumulada.")
                        .activo(true)
                        .diasMoraDesde(1)
                        .diasMoraHasta(15)
                        .frecuenciaDias(3)          // cada 3 días
                        .horarioDesde("09:00")
                        .horarioHasta("18:00")
                        .storeId(null)              // global
                        .prioridad(1)
                        .creadoEn(ahora)
                        .actualizadoEn(ahora)
                        .creadoPor("SISTEMA")
                        .build(),

                EstrategiaDocument.builder()
                        .id("est-mora-media-wa")
                        .nombre("WA Mora Media D+16 a D+30")
                        .nivel("MORA_MEDIA")
                        .canal("WHATSAPP")
                        .plantillaId(null)
                        .mensaje("Recordatorio urgente: mora en tramo medio. Regularice su pago.")
                        .activo(true)
                        .diasMoraDesde(16)
                        .diasMoraHasta(30)
                        .frecuenciaDias(2)          // cada 2 días
                        .horarioDesde("09:00")
                        .horarioHasta("18:00")
                        .storeId(null)
                        .prioridad(2)
                        .creadoEn(ahora)
                        .actualizadoEn(ahora)
                        .creadoPor("SISTEMA")
                        .build(),

                EstrategiaDocument.builder()
                        .id("est-mora-critica-wa")
                        .nombre("WA Mora Crítica D+31 a D+60")
                        .nivel("MORA_CRITICA")
                        .canal("WHATSAPP")
                        .plantillaId(null)
                        .mensaje("Aviso crítico: su deuda está en mora crítica. Contáctenos hoy.")
                        .activo(true)
                        .diasMoraDesde(31)
                        .diasMoraHasta(60)
                        .frecuenciaDias(1)          // diario
                        .horarioDesde("09:00")
                        .horarioHasta("17:00")
                        .storeId(null)
                        .prioridad(3)
                        .creadoEn(ahora)
                        .actualizadoEn(ahora)
                        .creadoPor("SISTEMA")
                        .build(),

                EstrategiaDocument.builder()
                        .id("est-judicial-wa")
                        .nombre("WA Pre-Judicial D+61 en adelante")
                        .nivel("JUDICIAL")
                        .canal("WHATSAPP")
                        .plantillaId(null)
                        .mensaje("Última notificación antes de proceso judicial. Regularice su deuda.")
                        .activo(true)
                        .diasMoraDesde(61)
                        .diasMoraHasta(null)        // sin límite superior
                        .frecuenciaDias(1)
                        .horarioDesde("09:00")
                        .horarioHasta("17:00")
                        .storeId(null)
                        .prioridad(4)
                        .creadoEn(ahora)
                        .actualizadoEn(ahora)
                        .creadoPor("SISTEMA")
                        .build()
        );
    }
}
