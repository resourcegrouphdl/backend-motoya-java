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
        if (!schedulerEnabled) {
            log.debug("[SCHEDULER] Desactivado — omitiendo ciclo");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.debug("[SCHEDULER] Ciclo ya en ejecución, omitiendo...");
            return;
        }

        log.info("[SCHEDULER] Iniciando ciclo de procesamiento de notificaciones pendientes...");

        java.util.concurrent.atomic.AtomicInteger procesados = new java.util.concurrent.atomic.AtomicInteger(0);

        eventRepository.findPendingEventsReadyForRetry(Instant.now())
                .doOnNext(event -> log.info("[SCHEDULER] Evento encontrado | id={} canal={} destinatario={} plantilla={}",
                        event.id(), event.channel(), event.recipient(), event.template()))
                .flatMap(event -> processUseCase.process(event)
                        .doOnSuccess(v -> procesados.incrementAndGet())
                        .onErrorResume(ex -> {
                            log.error("[SCHEDULER] Error procesando evento id={}: {}", event.id(), ex.getMessage());
                            return Mono.empty();
                        })
                )
                .doOnComplete(() -> log.info("[SCHEDULER] Ciclo completado | procesados={}", procesados.get()))
                .doOnError(ex -> log.error("[SCHEDULER] Error en ciclo: {}", ex.getMessage()))
                .doFinally(signal -> running.set(false))
                .subscribe();
    }
}
