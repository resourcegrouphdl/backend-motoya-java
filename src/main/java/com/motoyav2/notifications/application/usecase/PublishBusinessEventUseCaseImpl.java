package com.motoyav2.notifications.application.usecase;

import com.motoyav2.notifications.domain.events.BusinessEventType;
import com.motoyav2.notifications.domain.model.*;
import com.motoyav2.notifications.domain.ports.in.PublishBusinessEventUseCase;
import com.motoyav2.notifications.domain.ports.out.NotificationEventRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Persiste el evento en el Outbox (Firestore).
 * El scheduler lo procesará de forma asíncrona.
 *
 * Para migrar a Kafka: reemplazar la implementación por un KafkaTemplate.send()
 * sin cambiar la interfaz PublishBusinessEventUseCase ni los módulos que la usan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishBusinessEventUseCaseImpl implements PublishBusinessEventUseCase {

    private final NotificationEventRepositoryPort eventRepository;

    @Override
    public Mono<Void> publish(
            BusinessEventType eventType,
            String contratoId,
            NotificationChannel channel,
            String recipient,
            NotificationTemplate template,
            Map<String, String> variables) {

        NotificationEvent event = NotificationEvent.create(
                eventType, contratoId, channel, recipient, template, variables);

        return eventRepository.save(event)
                .doOnSuccess(saved -> log.info(
                        "[OUTBOX] Evento publicado | tipo={} contratoId={} id={}",
                        eventType, contratoId, saved.id()))
                .then();
    }
}
