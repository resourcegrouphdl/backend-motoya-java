package com.motoyav2.notifications.infrastructure.adapter.in.web;

import com.motoyav2.notifications.application.usecase.ProcessNotificationEventUseCaseImpl;
import com.motoyav2.notifications.domain.model.NotificationEvent;
import com.motoyav2.notifications.domain.model.NotificationEventStatus;
import com.motoyav2.notifications.domain.ports.out.NotificationEventRepositoryPort;
import com.motoyav2.notifications.domain.ports.out.NotificationRepositoryPort;
import com.motoyav2.notifications.infrastructure.adapter.in.web.dto.NotificationEventResponse;
import com.motoyav2.notifications.infrastructure.adapter.in.web.dto.NotificationLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Endpoints de consulta y gestión del Outbox de notificaciones.
 * Usados por el panel de administración para trazabilidad y reintentos manuales.
 *
 * GET  /api/v1/notifications/events                → lista paginada con filtros
 * GET  /api/v1/notifications/events/{id}           → detalle de un evento
 * GET  /api/v1/notifications/events/{id}/logs      → logs de auditoría del evento
 * POST /api/v1/notifications/events/{id}/retry     → reintento manual (solo FALLIDO)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/events")
@Tag(name = "Notificaciones - Trazabilidad", description = "Consulta y gestión del Outbox de notificaciones")
public class NotificationQueryController {

    private final NotificationEventRepositoryPort eventRepository;
    private final NotificationRepositoryPort notificationRepository;
    private final ProcessNotificationEventUseCaseImpl processUseCase;

    /**
     * Lista de eventos con filtros opcionales. Todos los parámetros son opcionales.
     */
    @GetMapping
    @Operation(
            summary = "Listar eventos de notificación",
            description = "Retorna eventos del Outbox con filtros opcionales. Ordenados por createdAt DESC.")
    public Flux<NotificationEventResponse> listEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String contratoId,
            @RequestParam(defaultValue = "50") int limit) {

        int safeLimit = Math.min(limit, 200);
        log.debug("[NOTIF-QUERY] Listando eventos | eventType={} status={} channel={} contratoId={} limit={}",
                eventType, status, channel, contratoId, safeLimit);

        return eventRepository.findByFilters(eventType, status, channel, contratoId, safeLimit)
                .map(NotificationEventResponse::from);
    }

    /**
     * Detalle de un evento específico.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Detalle de evento", description = "Retorna los datos completos de un evento del Outbox.")
    public Mono<NotificationEventResponse> getEvent(@PathVariable String id) {
        return eventRepository.findById(id)
                .map(NotificationEventResponse::from)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Evento no encontrado: " + id)));
    }

    /**
     * Logs de auditoría de todos los intentos de envío de un evento.
     * Permite construir la timeline completa: cada intento, resultado, wamid/messageId.
     */
    @GetMapping("/{id}/logs")
    @Operation(
            summary = "Logs de auditoría del evento",
            description = "Retorna el historial de intentos de envío (colección notifications filtrada por eventId).")
    public Flux<NotificationLogResponse> getEventLogs(@PathVariable String id) {
        return notificationRepository.findByEventId(id)
                .map(NotificationLogResponse::from);
    }

    /**
     * Reintento manual de un evento FALLIDO.
     * Restaura el estado a PENDIENTE con nextRetryAt = ahora para que el scheduler lo procese
     * en el siguiente ciclo, o lo procesa inmediatamente.
     */
    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Reintento manual",
            description = "Fuerza el reintento de un evento en estado FALLIDO. " +
                    "Solo funciona si el evento está en estado FALLIDO.")
    public Mono<NotificationEventResponse> retryEvent(@PathVariable String id) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Evento no encontrado: " + id)))
                .flatMap(event -> {
                    if (event.status() != NotificationEventStatus.FALLIDO) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Solo se pueden reintentar eventos en estado FALLIDO. Estado actual: "
                                        + event.status()));
                    }

                    log.info("[NOTIF-RETRY] Reintento manual | id={} tipo={} destinatario={}",
                            event.id(), event.eventType(), event.recipient());

                    // Restaurar a PENDIENTE con nextRetryAt = ahora (procesamiento inmediato)
                    NotificationEventStatus pendienteStatus = NotificationEventStatus.PENDIENTE;
                    var retryEvent = new NotificationEvent(
                            event.id(),
                            event.eventType(),
                            event.contratoId(),
                            event.channel(),
                            event.recipient(),
                            event.template(),
                            event.variables(),
                            pendienteStatus,
                            0,                  // reset retryCount para darle N intentos frescos
                            Instant.now(),      // nextRetryAt = ahora
                            event.createdAt(),
                            null                // limpiar processedAt
                    );

                    return eventRepository.update(retryEvent)
                            .flatMap(updated -> processUseCase.process(updated)
                                    .onErrorResume(ex -> {
                                        log.warn("[NOTIF-RETRY] Proceso fallido después de retry manual | id={} error={}",
                                                id, ex.getMessage());
                                        return Mono.empty();
                                    })
                                    .then(eventRepository.findById(id)));
                })
                .map(NotificationEventResponse::from);
    }
}
