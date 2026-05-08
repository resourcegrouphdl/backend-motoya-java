package com.motoyav2.contabilidad.domain.port.out;

import com.motoyav2.contabilidad.domain.model.PuntoRecaudacion;
import reactor.core.publisher.Flux;

public interface CronogramaPort {

    /**
     * Proyecta el flujo de caja esperado de los próximos {@code meses} meses
     * leyendo los cronogramas de contratos activos.
     */
    Flux<PuntoRecaudacion> proyectarFlujo(int meses, String tiendaId);
}
