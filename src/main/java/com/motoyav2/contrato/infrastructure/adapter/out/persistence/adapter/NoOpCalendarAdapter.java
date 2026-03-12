package com.motoyav2.contrato.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.port.out.CrearEventoEnCalendar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adaptador de calendario sin operación.
 * Se activa únicamente cuando google.calendar.client-email NO está configurado
 * (es decir, cuando CalendarGoogleAdapeter no se crea por su @ConditionalOnExpression).
 *
 * Evita que AprobarContratoService falle al arrancar por falta del bean.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(CrearEventoEnCalendar.class)
public class NoOpCalendarAdapter implements CrearEventoEnCalendar {

    @Override
    public Mono<Void> crearEventoEnCalendar(Contrato contrato) {
        log.debug("[Calendar] Google Calendar no configurado — evento omitido para contratoId={}",
                contrato.id());
        return Mono.empty();
    }
}
