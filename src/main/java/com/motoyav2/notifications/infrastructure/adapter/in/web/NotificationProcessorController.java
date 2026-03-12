package com.motoyav2.notifications.infrastructure.adapter.in.web;

import com.motoyav2.notifications.application.usecase.ProcessNotificationEventUseCaseImpl;
import com.motoyav2.notifications.domain.ports.out.NotificationEventRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Endpoint interno para procesar eventos de notificación pendientes.
 *
 * Llamado por:
 *   A) Cloud Function con Firestore trigger (evento nuevo) → POST /internal/notifications/process
 *   B) Cloud Scheduler cada 1 min (reintentos)            → POST /internal/notifications/process
 *
 * Seguridad: token secreto en header X-Internal-Token.
 * En producción usar IAM + service account de GCP para mayor seguridad.
 */
@Slf4j
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class NotificationProcessorController {

    private final NotificationEventRepositoryPort eventRepository;
    private final ProcessNotificationEventUseCaseImpl processUseCase;

    @Value("${notifications.internal.token}")
    private String internalToken;

    /**
     * Procesa todos los eventos PENDIENTE listos para enviar.
     * Cloud Function lo llama cuando detecta un nuevo documento en notification_events.
     * Cloud Scheduler lo llama cada 1 min para manejar reintentos.
     */
    @PostMapping("/process")
    public Mono<ProcessResponse> processEvents(
            @RequestHeader("X-Internal-Token") String token) {

        if (!internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido");
        }

        log.info("[PROCESSOR] Procesando eventos pendientes...");

        return eventRepository.findPendingEventsReadyForRetry(Instant.now())
                .flatMap(event -> processUseCase.process(event)
                        .onErrorResume(ex -> {
                            log.error("[PROCESSOR] Error procesando evento id={}: {}",
                                    event.id(), ex.getMessage());
                            return Mono.empty();
                        })
                        .thenReturn(1)
                )
                .reduce(0, Integer::sum)
                .map(count -> {
                    log.info("[PROCESSOR] {} evento(s) procesados", count);
                    return new ProcessResponse(count, "ok");
                });
    }

    public record ProcessResponse(int processed, String status) {}
}
