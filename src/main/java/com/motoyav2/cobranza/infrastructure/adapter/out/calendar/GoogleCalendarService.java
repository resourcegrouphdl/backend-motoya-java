package com.motoyav2.cobranza.infrastructure.adapter.out.calendar;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.motoyav2.cobranza.application.dto.EventoCalendarioParseado;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GoogleCalendarService {

    // Regex: "08.VALDEZ MOTA RAFAEL DANIEL S/339.00"
    // Groups: (cuotaNum)(nombreCompleto)(monto)
    private static final Pattern EVENTO_PATTERN =
            Pattern.compile("^(\\d+)\\.\\s*(.+?)\\s+(S/[\\d,\\.]+)$", Pattern.CASE_INSENSITIVE);

    private Calendar buildCalendarService() throws Exception {
        GoogleCredentials credentials = GoogleCredentials
                .getApplicationDefault()
                .createScoped(Collections.singleton(CalendarScopes.CALENDAR_READONLY));
        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName("Motoya v2 Cobranzas").build();
    }

    /**
     * Obtiene y parsea todos los eventos del calendario.
     * Agrupa por nombreCompleto: un cliente puede tener múltiples cuotas.
     *
     * @return Flux de EventoCalendarioParseado (uno por evento)
     */
    public Flux<EventoCalendarioParseado> obtenerEventos(String calendarId) {
        return Mono.fromCallable(() -> {
                    Calendar service = buildCalendarService();
                    Events result = service.events().list(calendarId)
                            .setSingleEvents(true)
                            .setOrderBy("startTime")
                            .execute();
                    return result.getItems() != null ? result.getItems() : Collections.<Event>emptyList();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(events -> Flux.fromIterable(events))
                .flatMap(event -> {
                    String titulo = event.getSummary();
                    if (titulo == null) return Flux.empty();

                    Matcher m = EVENTO_PATTERN.matcher(titulo.trim());
                    if (!m.matches()) {
                        log.warn("[Calendar] Evento ignorado (formato no reconocido): {}", titulo);
                        return Flux.empty();
                    }

                    int numeroCuota = Integer.parseInt(m.group(1));
                    String nombreCompleto = m.group(2).trim();
                    double monto = parseMonto(m.group(3));
                    LocalDate fechaVencimiento = parseFechaEvento(event);

                    return Flux.just(new EventoCalendarioParseado(
                            nombreCompleto, numeroCuota, monto, fechaVencimiento, titulo
                    ));
                });
    }

    private double parseMonto(String montoStr) {
        // "S/339.00" or "S/1,500.00"
        return Double.parseDouble(montoStr.replaceAll("[S/,\\s]", ""));
    }

    private LocalDate parseFechaEvento(Event event) {
        try {
            if (event.getStart().getDate() != null) {
                // All-day event: "2026-02-23"
                return LocalDate.parse(event.getStart().getDate().toStringRfc3339());
            } else if (event.getStart().getDateTime() != null) {
                long millis = event.getStart().getDateTime().getValue();
                return new java.util.Date(millis).toInstant()
                        .atZone(java.time.ZoneId.of("America/Lima"))
                        .toLocalDate();
            }
        } catch (Exception e) {
            log.warn("[Calendar] No se pudo parsear fecha del evento: {}", e.getMessage());
        }
        return null;
    }
}
