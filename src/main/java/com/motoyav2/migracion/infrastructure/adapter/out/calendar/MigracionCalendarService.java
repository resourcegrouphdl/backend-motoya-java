package com.motoyav2.migracion.infrastructure.adapter.out.calendar;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.motoyav2.calendar.config.GoogleCalendarProperties;
import com.motoyav2.migracion.config.MigracionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee eventos del calendario "clientes" de motoyadigital@gmail.com.
 * Reutiliza el bean provisionalCalendarApi (mismas credenciales service account).
 * El calendarId se configura via migracion.calendar-id (distinto al calendar.calendar-id del módulo escritura).
 */
@Slf4j
@Component
public class MigracionCalendarService {

    // Formato: "01. VALDEZ RAFAEL S/339.00" o "01. Juan Perez S/. 320"
    private static final Pattern EVENTO_PATTERN =
            Pattern.compile("^(\\d+)\\.\\s*(.+?)\\s+S/\\.?\\s*([\\d,\\.]+)$", Pattern.CASE_INSENSITIVE);

    private final MigracionProperties props;
    private final GoogleCalendarProperties calendarProps;

    @Autowired(required = false)
    @Qualifier("provisionalCalendarApi")
    private Calendar calendarApi;

    public MigracionCalendarService(MigracionProperties props, GoogleCalendarProperties calendarProps) {
        this.props = props;
        this.calendarProps = calendarProps;
    }

    /**
     * Obtiene y parsea todos los eventos del calendario "clientes".
     * Lee el colorId de cada evento para determinar si la cuota está pagada.
     */
    public Flux<EventoMigracionParseado> obtenerEventos() {
        if (calendarApi == null) {
            log.error("[Migracion-Calendar] Google Calendar API no disponible. Verificar google.calendar.client-email");
            return Flux.error(new IllegalStateException(
                    "Google Calendar no está configurado. Verificar credenciales en application.properties"));
        }

        // Prioridad: google.calendar.read-id → migracion.calendar-id
        String readId = calendarProps.getReadId();
        String calendarId = (readId != null && !readId.isBlank()) ? readId : props.getCalendarId();
        if (calendarId == null || calendarId.isBlank()) {
            return Flux.error(new IllegalStateException(
                    "Calendario de migración no configurado. Definir google.calendar.read-id o MIGRACION_CALENDAR_ID."));
        }

        log.info("[Migracion-Calendar] Leyendo eventos del calendario: {}", calendarId);

        return Mono.fromCallable(() -> {
                    Events result = calendarApi.events().list(calendarId)
                            .setSingleEvents(true)
                            .setOrderBy("startTime")
                            .execute();
                    return result.getItems() != null ? result.getItems() : Collections.<Event>emptyList();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(events -> log.info("[Migracion-Calendar] {} eventos obtenidos", events.size()))
                .flatMapMany(Flux::fromIterable)
                .flatMap(this::parsearEvento);
    }

    private Mono<EventoMigracionParseado> parsearEvento(Event event) {
        String titulo = event.getSummary();
        if (titulo == null || titulo.isBlank()) return Mono.empty();

        Matcher m = EVENTO_PATTERN.matcher(titulo.trim());
        if (!m.matches()) {
            log.debug("[Migracion-Calendar] Evento ignorado (formato no reconocido): {}", titulo);
            return Mono.empty();
        }

        int numeroCuota = Integer.parseInt(m.group(1));
        String nombreCompleto = m.group(2).trim();
        double monto = parseMonto(m.group(3));
        LocalDate fechaVencimiento = parseFechaEvento(event);

        // colorId 2 (Sage/verde) o 10 (Basil/verde oscuro) = cuota pagada
        String colorId = event.getColorId();
        boolean pagada = colorId != null && props.getColorPagadoIds().contains(colorId);

        return Mono.just(new EventoMigracionParseado(
                nombreCompleto, numeroCuota, monto, fechaVencimiento, titulo, pagada
        ));
    }

    private double parseMonto(String montoStr) {
        try {
            return Double.parseDouble(montoStr.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException e) {
            log.warn("[Migracion-Calendar] No se pudo parsear monto: {}", montoStr);
            return 0.0;
        }
    }

    private LocalDate parseFechaEvento(Event event) {
        try {
            if (event.getStart().getDate() != null) {
                return LocalDate.parse(event.getStart().getDate().toStringRfc3339());
            } else if (event.getStart().getDateTime() != null) {
                long millis = event.getStart().getDateTime().getValue();
                return new java.util.Date(millis).toInstant()
                        .atZone(java.time.ZoneId.of("America/Lima"))
                        .toLocalDate();
            }
        } catch (Exception e) {
            log.warn("[Migracion-Calendar] No se pudo parsear fecha: {}", e.getMessage());
        }
        return null;
    }
}
