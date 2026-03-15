package com.motoyav2.calendar.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.cloud.Timestamp;
import com.motoyav2.calendar.config.GoogleCalendarProperties;
import com.motoyav2.calendar.dto.CronogramaRequest;
import com.motoyav2.calendar.dto.CronogramaResponse;
import com.motoyav2.calendar.dto.CuotaRequest;
import com.motoyav2.calendar.firestore.CalendarCronogramaDocument;
import com.motoyav2.calendar.firestore.CalendarCronogramaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio que crea eventos todo-el-día en Google Calendar y persiste el resultado en Firestore.
 * Responsabilidad única: escribir el cronograma de cuotas como respaldo en Calendar.
 * La notificación al módulo de Cobranzas ocurre de forma independiente via CobranzaIntegrationPort.
 *
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${google.calendar.client-email:}' != ''")
public class CalendarCronogramaService {

    private static final Pattern PATRON_NOMBRE_MONTO =
            Pattern.compile("^(.+?)\\s+[Ss]/\\.?\\s*([\\d,\\.]+)\\s*$");

    private final Calendar calendarApi;
    private final GoogleCalendarProperties calendarProps;
    private final CalendarCronogramaRepository repository;

    // ─────────────────────────────────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Genera el cronograma: crea un evento todo-el-día por cada cuota
     * y guarda el resultado en Firestore.
     */
    public Mono<CronogramaResponse> generarCronograma(CronogramaRequest request) {
        NombreParseado parsed = parsearNombreYMonto(request.getNombreCliente());

        log.info("[Calendar] Iniciando cronograma para '{}' — monto={}, {} cuotas, estado={}",
                parsed.nombre(), parsed.montoFormateado(), request.getCuotas().size(), request.getEstado());

        return Flux.fromIterable(request.getCuotas())
                .concatMap(cuota -> crearEventoConManejo(cuota, request, parsed.nombre()))
                .collectList()
                .flatMap(resultados -> guardarYResponder(request, resultados, parsed));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Creación de evento
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<EventoResultado> crearEventoConManejo(
            CuotaRequest cuota, CronogramaRequest req, String nombreLimpio) {
        return Mono.fromCallable(() -> insertarEvento(cuota, req, nombreLimpio))
                .subscribeOn(Schedulers.boundedElastic())
                .map(eventId -> {
                    log.debug("[Calendar] Evento creado — cuota #{} fecha={} eventId={}",
                            cuota.getNumero(), cuota.getFecha(), eventId);
                    return EventoResultado.exito(cuota, eventId, calendarProps.getCalendarId());
                })
                .onErrorResume(e -> {
                    log.error("[Calendar] Error al crear evento — cuota #{} fecha={}: {}",
                            cuota.getNumero(), cuota.getFecha(), e.getMessage());
                    return Mono.just(EventoResultado.error(cuota, e.getMessage()));
                });
    }

    private String insertarEvento(
            CuotaRequest cuota, CronogramaRequest req, String nombreLimpio) throws Exception {
        Event evento = new Event()
                .setSummary(cuota.getNumero() + "." + nombreLimpio)
                .setDescription(req.getDescripcion()
                        + "\nEstado: " + req.getEstado()
                        + "\nCuota: " + cuota.getNumero())
                .setColorId(resolveColorId(req.getEstado()))
                .setStart(toEventDateTime(cuota.getFecha()))
                .setEnd(toEventDateTime(cuota.getFecha().plusDays(1)));

        return calendarApi.events()
                .insert(calendarProps.getCalendarId(), evento)
                .execute()
                .getId();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistencia en Firestore
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<CronogramaResponse> guardarYResponder(
            CronogramaRequest request, List<EventoResultado> resultados, NombreParseado parsed) {

        List<CalendarCronogramaDocument.EventoInfo> eventosExitosos = resultados.stream()
                .filter(EventoResultado::esExitoso)
                .map(r -> CalendarCronogramaDocument.EventoInfo.builder()
                        .numero(r.numero())
                        .fecha(r.fecha())
                        .eventId(r.eventId())
                        .calendarId(r.calendarId())
                        .build())
                .toList();

        List<CronogramaResponse.EventoError> errores = resultados.stream()
                .filter(r -> !r.esExitoso())
                .map(r -> CronogramaResponse.EventoError.builder()
                        .numeroCuota(r.numero())
                        .fecha(r.fecha())
                        .mensaje(r.errorMensaje())
                        .build())
                .toList();

        log.info("[Calendar] Resultado — creados={}, errores={}", eventosExitosos.size(), errores.size());

        CalendarCronogramaDocument documento = CalendarCronogramaDocument.builder()
                .nombreCliente(parsed.nombre())
                .descripcion(request.getDescripcion())
                .estado(request.getEstado())
                .totalCuotas(request.getCuotas().size())
                .createdAt(Timestamp.now())
                .eventos(eventosExitosos)
                .build();

        return repository.save(documento)
                .doOnSuccess(doc -> log.info("[Calendar] Cronograma guardado en Firestore — id={}", doc.getId()))
                .thenReturn(CronogramaResponse.builder()
                        .totalSolicitado(request.getCuotas().size())
                        .eventosCreados((int) resultados.stream().filter(EventoResultado::esExitoso).count())
                        .errores(errores)
                        .nombreCliente(parsed.nombre())
                        .montoCuota(parsed.montoFormateado())
                        .contratoId(request.getContratoId())
                        .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private NombreParseado parsearNombreYMonto(String raw) {
        if (raw == null) return new NombreParseado("", null);
        Matcher m = PATRON_NOMBRE_MONTO.matcher(raw.trim());
        if (!m.matches()) {
            log.warn("[Calendar] No se pudo extraer monto de nombreCliente='{}'", raw);
            return new NombreParseado(raw.trim(), null);
        }
        String nombre = m.group(1).trim();
        String montoStr = m.group(2).replace(",", "");
        try {
            BigDecimal monto = new BigDecimal(montoStr).setScale(2, RoundingMode.HALF_UP);
            return new NombreParseado(nombre, monto);
        } catch (NumberFormatException e) {
            log.warn("[Calendar] Monto '{}' no es numérico en nombreCliente='{}'", montoStr, raw);
            return new NombreParseado(nombre, null);
        }
    }

    private String resolveColorId(String estado) {
        return switch (estado.toUpperCase()) {
            case "PAGADO"   -> "2";
            case "ATRASADO" -> "8";
            default         -> "7";
        };
    }

    private EventDateTime toEventDateTime(LocalDate fecha) {
        return new EventDateTime().setDate(new DateTime(fecha.toString()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Records internos
    // ─────────────────────────────────────────────────────────────────────────

    private record EventoResultado(
            int numero, String fecha, String eventId, String calendarId, String errorMensaje) {

        boolean esExitoso() { return eventId != null; }

        static EventoResultado exito(CuotaRequest c, String eventId, String calendarId) {
            return new EventoResultado(c.getNumero(), c.getFecha().toString(), eventId, calendarId, null);
        }

        static EventoResultado error(CuotaRequest c, String mensaje) {
            return new EventoResultado(c.getNumero(), c.getFecha().toString(), null, null, mensaje);
        }
    }

    private record NombreParseado(String nombre, BigDecimal monto) {
        String montoFormateado() {
            return monto != null ? "S/." + monto.toPlainString() : null;
        }
    }
}