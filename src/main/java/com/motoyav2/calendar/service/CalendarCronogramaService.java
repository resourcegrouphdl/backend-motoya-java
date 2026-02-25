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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;

/**
 * Servicio que orquesta la creación de eventos todo-el-día en Google Calendar
 * y persiste el resultado en Firestore.
 *
 * Estrategia de errores: si un evento falla se continúa con el siguiente.
 * Al final se guarda en Firestore solo los eventos creados exitosamente.
 *
 * MÓDULO PROVISIONAL — eliminar junto con el package com.motoyav2.calendar/
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarCronogramaService {

    // Único bean Calendar en el contexto (definido en GoogleCalendarConfig)
    private final Calendar calendarApi;

    private final GoogleCalendarProperties calendarProps;
    private final CalendarCronogramaRepository repository;

    // ─────────────────────────────────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Genera el cronograma: crea un evento todo-el-día por cada cuota y guarda
     * el resultado en la colección Firestore {@code calendar_cronogramas}.
     *
     * @param request datos del cronograma con lista de cuotas
     * @return resumen con total creados y lista de errores
     */
    public Mono<CronogramaResponse> generarCronograma(CronogramaRequest request) {
        log.info("[Calendar] Iniciando cronograma para '{}' — {} cuotas, estado={}",
                request.getNombreCliente(), request.getCuotas().size(), request.getEstado());

        return Flux.fromIterable(request.getCuotas())
                // concatMap: crea eventos en orden secuencial (evita saturar la API de Google)
                .concatMap(cuota -> crearEventoConManejo(cuota, request))
                .collectList()
                .flatMap(resultados -> guardarYResponder(request, resultados));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Creación de evento (con manejo de error por cuota)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Intenta crear el evento en Google Calendar.
     * Si falla, registra el error y retorna un resultado de error sin lanzar excepción,
     * permitiendo que el flujo continúe con las demás cuotas.
     */
    private Mono<EventoResultado> crearEventoConManejo(CuotaRequest cuota, CronogramaRequest req) {
        return Mono.fromCallable(() -> insertarEvento(cuota, req))
                .subscribeOn(Schedulers.boundedElastic()) // llamada bloqueante → hilo dedicado
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

    /**
     * Llama a la Google Calendar API para insertar el evento.
     * Método bloqueante — se ejecuta en Schedulers.boundedElastic().
     */
    private String insertarEvento(CuotaRequest cuota, CronogramaRequest req) throws Exception {
        Event evento = new Event()
                .setSummary(buildSummary(cuota.getNumero(), req.getNombreCliente()))
                .setDescription(buildDescription(req, cuota.getNumero()))
                .setColorId(resolveColorId(req.getEstado()))
                .setStart(toEventDateTime(cuota.getFecha()))
                .setEnd(toEventDateTime(cuota.getFecha().plusDays(1)));

        return calendarApi.events()
                .insert(calendarProps.getCalendarId(), evento)
                .execute()
                .getId();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistencia en Firestore y construcción de respuesta
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<CronogramaResponse> guardarYResponder(
            CronogramaRequest request, List<EventoResultado> resultados) {

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

        log.info("[Calendar] Resultado — creados={}, errores={}",
                eventosExitosos.size(), errores.size());

        CalendarCronogramaDocument documento = CalendarCronogramaDocument.builder()
                .nombreCliente(request.getNombreCliente())
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
                        .eventosCreados(eventosExitosos.size())
                        .errores(errores)
                        .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Summary del evento: "1.Juan Perez" */
    private String buildSummary(int numero, String nombreCliente) {
        return numero + "." + nombreCliente;
    }

    /**
     * Descripción del evento con contexto de la cuota.
     * Ejemplo:
     *   Prestamo S/ 5,000
     *   Estado: PENDIENTE
     *   Cuota: 1
     */
    private String buildDescription(CronogramaRequest req, int numeroCuota) {
        return req.getDescripcion()
                + "\nEstado: " + req.getEstado()
                + "\nCuota: " + numeroCuota;
    }

    /**
     * Mapeo de estado → colorId de Google Calendar.
     * PENDIENTE → 4 (Flamingo), PAGADO → 2 (Sage), ATRASADO → 8 (Graphite)
     */
    private String resolveColorId(String estado) {
        return switch (estado.toUpperCase()) {
            case "PAGADO"   -> "2";
            case "ATRASADO" -> "8";
            default         -> "4"; // PENDIENTE y cualquier otro estado
        };
    }

    /**
     * Convierte LocalDate a EventDateTime de Google Calendar para evento todo el día.
     * start.date = fecha, end.date = fecha + 1 (exclusivo, exigido por la API).
     */
    private EventDateTime toEventDateTime(LocalDate fecha) {
        return new EventDateTime().setDate(new DateTime(fecha.toString()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Record interno para acumular resultados por cuota
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resultado de intentar crear un evento.
     * Si {@code eventId} es null el evento falló.
     */
    private record EventoResultado(
            int numero,
            String fecha,
            String eventId,
            String calendarId,
            String errorMensaje
    ) {
        boolean esExitoso() {
            return eventId != null;
        }

        static EventoResultado exito(CuotaRequest c, String eventId, String calendarId) {
            return new EventoResultado(c.getNumero(), c.getFecha().toString(), eventId, calendarId, null);
        }

        static EventoResultado error(CuotaRequest c, String mensaje) {
            return new EventoResultado(c.getNumero(), c.getFecha().toString(), null, null, mensaje);
        }
    }
}
