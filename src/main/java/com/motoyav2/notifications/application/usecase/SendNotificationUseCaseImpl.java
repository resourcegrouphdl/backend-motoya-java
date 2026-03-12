package com.motoyav2.notifications.application.usecase;

import com.motoyav2.notifications.domain.model.*;
import com.motoyav2.notifications.domain.ports.in.SendNotificationUseCase;
import com.motoyav2.notifications.domain.ports.out.NotificationRepositoryPort;
import com.motoyav2.notifications.domain.ports.out.NotificationSenderPort;
import com.motoyav2.notifications.domain.ports.out.TemplateRendererPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class SendNotificationUseCaseImpl implements SendNotificationUseCase {

    private final NotificationRepositoryPort notificationRepository;
    private final TemplateRendererPort templateRenderer;
    private final List<NotificationSenderPort> senders;

    public SendNotificationUseCaseImpl(
            NotificationRepositoryPort notificationRepository,
            TemplateRendererPort templateRenderer,
            List<NotificationSenderPort> senders) {
        this.notificationRepository = notificationRepository;
        this.templateRenderer = templateRenderer;
        this.senders = senders;
    }

    @Override
    public Mono<Void> send(NotificationRequest request) {
        Notification notification = Notification.create(request);

        return notificationRepository.save(notification)
                .flatMap(saved -> templateRenderer.render(saved)
                        .flatMap(content -> {
                            Notification withContent = saved.withRenderedContent(content);
                            NotificationSenderPort sender = resolveSender(saved.channel());

                            return sender.send(withContent)
                                    .then(notificationRepository.updateStatus(
                                            saved.id(), NotificationStatus.ENVIADO, null))
                                    .doOnSuccess(r -> log.info(
                                            "[NOTIF] ✓ Enviado | canal={} plantilla={} destinatario={}",
                                            saved.channel(), saved.template(), saved.recipient()))
                                    .onErrorResume(ex -> {
                                        log.error("[NOTIF] ✗ Error | canal={} plantilla={} destinatario={} error={}",
                                                saved.channel(), saved.template(), saved.recipient(), ex.getMessage());
                                        return notificationRepository
                                                .updateStatus(saved.id(), NotificationStatus.FALLIDO, ex.getMessage())
                                                .then(Mono.error(ex));
                                    });
                        })
                )
                .then();
    }

    private NotificationSenderPort resolveSender(NotificationChannel channel) {
        return senders.stream()
                .filter(s -> s.channel() == channel)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay adaptador para canal: " + channel));
    }
}
