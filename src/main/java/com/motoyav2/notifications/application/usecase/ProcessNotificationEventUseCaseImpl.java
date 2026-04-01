package com.motoyav2.notifications.application.usecase;

import com.motoyav2.notifications.domain.model.NotificationEvent;
import com.motoyav2.notifications.domain.model.NotificationRequest;
import com.motoyav2.notifications.domain.ports.in.SendNotificationUseCase;
import com.motoyav2.notifications.domain.ports.out.NotificationEventRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Procesa un evento del Outbox: renderiza la plantilla, envía la notificación
 * y actualiza el estado del evento.
 *
 * Flujo:
 *   PENDIENTE → PROCESANDO → (éxito) COMPLETADO
 *                           → (fallo, retryCount < MAX) PENDIENTE con nuevo nextRetryAt
 *                           → (fallo, retryCount >= MAX) FALLIDO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessNotificationEventUseCaseImpl {

    private final NotificationEventRepositoryPort eventRepository;
    private final SendNotificationUseCase sendNotificationUseCase;

    public Mono<Void> process(NotificationEvent event) {
        log.info("[OUTBOX] Procesando | id={} tipo={} intento={}",
                event.id(), event.eventType(), event.retryCount() + 1);

        // 1. Marcar como PROCESANDO para evitar doble procesamiento
        return eventRepository.update(event.markProcessing())
                .flatMap(processing -> {
                    NotificationRequest request = NotificationRequest.builder()
                            .eventId(event.id())  // trazabilidad: enlaza log con evento outbox
                            .channel(event.channel())
                            .recipient(event.recipient())
                            .template(event.template())
                            .variables(event.variables())
                            .build();

                    // 2. Enviar notificación
                    return sendNotificationUseCase.send(request)
                            // 3a. Éxito → COMPLETADO (Mono<Void> para consistencia de tipos)
                            .then(eventRepository.update(event.markCompleted()).then())
                            .doOnSuccess(r -> log.info("[OUTBOX] ✓ Completado | id={} tipo={}",
                                    event.id(), event.eventType()))
                            // 3b. Error → programar reintento o marcar FALLIDO (ambos Mono<Void>)
                            .onErrorResume(ex -> handleFailure(event, ex));
                });
    }

    private Mono<Void> handleFailure(NotificationEvent event, Throwable ex) {
        if (event.hasExceededMaxRetries()) {
            log.error("[OUTBOX] ✗ FALLIDO definitivo | id={} tipo={} reintentos={} error={}",
                    event.id(), event.eventType(), event.retryCount(), ex.getMessage());
            return eventRepository.update(event.markFailed()).then();
        }

        NotificationEvent retryEvent = event.scheduleRetry();
        log.warn("[OUTBOX] ↺ Reintento {} programado | id={} tipo={} nextRetryAt={} error={}",
                retryEvent.retryCount(), event.id(), event.eventType(),
                retryEvent.nextRetryAt(), ex.getMessage());
        return eventRepository.update(retryEvent).then();
    }
}
