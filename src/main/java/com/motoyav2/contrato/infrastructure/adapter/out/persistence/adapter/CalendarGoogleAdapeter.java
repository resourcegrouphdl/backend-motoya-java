package com.motoyav2.contrato.infrastructure.adapter.out.persistence.adapter;

import com.motoyav2.calendar.dto.CronogramaRequest;
import com.motoyav2.calendar.service.CalendarCronogramaService;
import com.motoyav2.contrato.domain.model.Contrato;
import com.motoyav2.contrato.domain.port.out.CrearEventoEnCalendar;
import com.motoyav2.contrato.infrastructure.adapter.out.persistence.mapper.CronogramaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adaptador de Google Calendar.
 * Inyecta CalendarCronogramaService como opcional: si Google Calendar no está
 * configurado (client-email vacío), el servicio no existe como bean y el
 * adaptador opera en modo no-op sin fallar el arranque.
 */
@Component
@Slf4j
public class CalendarGoogleAdapeter implements CrearEventoEnCalendar {

    private final CalendarCronogramaService service;

    public CalendarGoogleAdapeter(
            @Autowired(required = false) CalendarCronogramaService service) {
        this.service = service;
    }

    @Override
    public Mono<Void> crearEventoEnCalendar(Contrato contrato) {
        if (service == null) {
            log.debug("[Calendar] Google Calendar no configurado — evento omitido para contratoId={}",
                    contrato.id());
            return Mono.empty();
        }

        CronogramaRequest request = CronogramaMapper.toRequest(contrato);
        return service.generarCronograma(request)
                .doOnSuccess(r -> log.info("[Calendar] Cronograma generado — contratoId={}", contrato.id()))
                .then();
    }
}
