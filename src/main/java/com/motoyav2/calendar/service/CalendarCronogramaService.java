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
import com.motoyav2.cobranza.application.port.in.IniciarCasoUseCase;
import com.motoyav2.cobranza.application.port.in.command.IniciarCasoCommand;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.CuotaCronogramaDocument;
import com.motoyav2.cobranza.infrastructure.adapter.out.persistence.document.embedded.DatosTitularDocument;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio que orquesta la creación de eventos todo-el-día en Google Calendar,
 * persiste el resultado en Firestore e inicia el caso de cobranza correspondiente.
 *
 * El campo {@code nombreCliente} se espera con el formato:
 * {@code "Apellido1 Apellido2 Nombre1 S/.{monto}"} — el monto se extrae automáticamente.
 *
 * Estrategia de errores: si un evento falla se continúa con el siguiente.
 * Al final se guarda en Firestore solo los eventos creados exitosamente.
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
    private final IniciarCasoUseCase iniciarCasoUseCase;

    // ─────────────────────────────────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Genera el cronograma: crea un evento todo-el-día por cada cuota,
     * guarda el resultado en Firestore e inicia el caso de cobranza si
     * el request incluye {@code contratoId} y {@code storeId}.
     *
     * @param request datos del cronograma con lista de cuotas
     * @return resumen con total creados, monto parseado y lista de errores
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
    // Creación de evento (con manejo de error por cuota)
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
                .setSummary(buildSummary(cuota.getNumero(), nombreLimpio))
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
    // Persistencia en Firestore + inicio de caso de cobranza
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
                .flatMap(doc -> iniciarCasoSiProcede(request, resultados, parsed));
    }

    /**
     * Si el request trae {@code contratoId} y {@code storeId}, crea/actualiza
     * el caso de cobranza con el cronograma completo y devuelve la respuesta
     * con el contratoId incluido.
     */
    private Mono<CronogramaResponse> iniciarCasoSiProcede(
            CronogramaRequest request, List<EventoResultado> resultados, NombreParseado parsed) {

        if (request.getContratoId() == null || request.getStoreId() == null) {
            log.info("[Calendar→Cobranza] Sin contratoId/storeId — omitiendo inicio de caso");
            return Mono.just(buildResponse(request, resultados, parsed, null));
        }

        List<CuotaCronogramaDocument> cronograma = buildCronograma(request, parsed);

        String fechaPrimeraCuota = cronograma.stream()
                .map(CuotaCronogramaDocument::getFechaVencimiento)
                .filter(f -> f != null)
                .min(Comparator.naturalOrder())
                .orElse(null);

        Double capitalOriginal = parsed.monto() != null
                ? parsed.monto().multiply(BigDecimal.valueOf(request.getCuotas().size())).doubleValue()
                : null;

        IniciarCasoCommand command = new IniciarCasoCommand(
                request.getContratoId(),
                request.getStoreId(),
                buildTitular(parsed.nombre()),
                request.getDescripcion(),
                capitalOriginal,
                capitalOriginal,           // saldoActual = capitalOriginal en el inicio
                "MORA_TEMPRANA",
                "EN_SEGUIMIENTO",
                request.getAgenteAsignadoId(),
                request.getAgenteAsignadoNombre(),
                fechaPrimeraCuota,
                cronograma,
                request.getCreadoPor()
        );

        return iniciarCasoUseCase.ejecutar(command)
                .doOnSuccess(caso -> log.info("[Calendar→Cobranza] Caso iniciado — contratoId={}", caso.getContratoId()))
                .thenReturn(buildResponse(request, resultados, parsed, request.getContratoId()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constructores de objetos auxiliares
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Convierte las cuotas del request en documentos de cronograma para Firestore.
     * El monto se toma del campo {@code parsed.monto()}.
     */
    private List<CuotaCronogramaDocument> buildCronograma(
            CronogramaRequest request, NombreParseado parsed) {
        String estadoCuota = mapearEstadoCuota(request.getEstado());
        return request.getCuotas().stream()
                .map(c -> CuotaCronogramaDocument.builder()
                        .cuota(c.getNumero())
                        .cuotaNum(c.getNumero())
                        .fechaVencimiento(c.getFecha().toString())
                        .monto(parsed.monto() != null ? parsed.monto().doubleValue() : null)
                        .estado(estadoCuota)
                        .build())
                .toList();
    }

    /**
     * Construye un {@link DatosTitularDocument} mínimo a partir del nombre completo.
     * Convención peruana: primeras 2 palabras = apellidos, resto = nombres.
     */
    private DatosTitularDocument buildTitular(String nombreCompleto) {
        String[] partes = nombreCompleto.trim().split("\\s+");
        String apellidos;
        String nombres;
        if (partes.length >= 3) {
            apellidos = partes[0] + " " + partes[1];
            nombres   = String.join(" ", Arrays.copyOfRange(partes, 2, partes.length));
        } else if (partes.length == 2) {
            apellidos = partes[0];
            nombres   = partes[1];
        } else {
            apellidos = nombreCompleto;
            nombres   = "";
        }
        return DatosTitularDocument.builder()
                .apellidos(apellidos)
                .nombres(nombres)
                .build();
    }

    private CronogramaResponse buildResponse(
            CronogramaRequest request, List<EventoResultado> resultados,
            NombreParseado parsed, String contratoId) {

        List<CronogramaResponse.EventoError> errores = resultados.stream()
                .filter(r -> !r.esExitoso())
                .map(r -> CronogramaResponse.EventoError.builder()
                        .numeroCuota(r.numero())
                        .fecha(r.fecha())
                        .mensaje(r.errorMensaje())
                        .build())
                .toList();

        return CronogramaResponse.builder()
                .totalSolicitado(request.getCuotas().size())
                .eventosCreados((int) resultados.stream().filter(EventoResultado::esExitoso).count())
                .errores(errores)
                .nombreCliente(parsed.nombre())
                .montoCuota(parsed.montoFormateado())
                .contratoId(contratoId)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Parsea "Garzon Aranzo Jhonatan S/.235" → nombre limpio + BigDecimal con 2 decimales */
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

    /** Mapea el estado del cronograma al estado de cada cuota */
    private String mapearEstadoCuota(String estadoCronograma) {
        return switch (estadoCronograma.toUpperCase()) {
            case "PAGADO"   -> "PAGADA";
            case "ATRASADO" -> "VENCIDA";
            default         -> "PENDIENTE";
        };
    }

    /** Summary del evento: "1.Juan Perez" */
    private String buildSummary(int numero, String nombreLimpio) {
        return numero + "." + nombreLimpio;
    }

    private String buildDescription(CronogramaRequest req, int numeroCuota) {
        return req.getDescripcion()
                + "\nEstado: " + req.getEstado()
                + "\nCuota: " + numeroCuota;
    }

    private String resolveColorId(String estado) {
        return switch (estado.toUpperCase()) {
            case "PAGADO"   -> "2";
            case "ATRASADO" -> "8";
            default         -> "4";
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

    /**
     * Resultado del parsing de {@code nombreCliente}.
     * @param nombre nombre limpio sin el sufijo "S/.{monto}"
     * @param monto  BigDecimal con escala 2, o null si no se pudo parsear
     */
    private record NombreParseado(String nombre, BigDecimal monto) {
        /** Devuelve "S/.235.00" o null si no hay monto */
        String montoFormateado() {
            return monto != null ? "S/." + monto.toPlainString() : null;
        }
    }
}