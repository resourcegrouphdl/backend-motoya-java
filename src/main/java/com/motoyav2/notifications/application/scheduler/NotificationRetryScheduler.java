package com.motoyav2.notifications.application.scheduler;

import com.motoyav2.notifications.application.usecase.ProcessNotificationEventUseCaseImpl;
import com.motoyav2.notifications.domain.ports.out.NotificationEventRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduler del Outbox: cada N segundos busca eventos PENDIENTE listos para
 * procesar (nextRetryAt <= ahora) y los envía.
 *
 * Usa AtomicBoolean para evitar ejecuciones concurrentes si un ciclo tarda más
 * que el intervalo configurado.
 *
 * Intervalo configurable: notifications.scheduler.delay-ms (default: 30 segundos)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationEventRepositoryPort eventRepository;
    private final ProcessNotificationEventUseCaseImpl processUseCase;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @org.springframework.beans.factory.annotation.Value("${notifications.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @Scheduled(fixedDelayString = "${notifications.scheduler.delay-ms:30000}")
    public void processPendingEvents() {
        if (!schedulerEnabled) return;

        if (!running.compareAndSet(false, true)) {
            log.debug("[SCHEDULER] Ciclo ya en ejecución, omitiendo...");
            return;
        }

        log.debug("[SCHEDULER] Iniciando ciclo de procesamiento de eventos...");

        eventRepository.findPendingEventsReadyForRetry(Instant.now())
                .flatMap(event -> processUseCase.process(event)
                        .onErrorResume(ex -> {
                            log.error("[SCHEDULER] Error procesando evento id={}: {}",
                                    event.id(), ex.getMessage());
                            return Mono.empty();
                        })
                )
                .doOnComplete(() -> log.debug("[SCHEDULER] Ciclo completado"))
                .doOnError(ex -> log.error("[SCHEDULER] Error en ciclo: {}", ex.getMessage()))
                .doFinally(signal -> running.set(false))
                .subscribe();
    }
}
