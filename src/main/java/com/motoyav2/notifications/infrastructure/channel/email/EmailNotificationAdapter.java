package com.motoyav2.notifications.infrastructure.channel.email;

import com.motoyav2.notifications.domain.model.Notification;
import com.motoyav2.notifications.domain.model.NotificationChannel;
import com.motoyav2.notifications.domain.ports.out.NotificationSenderPort;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Adaptador de salida: envío de emails vía JavaMail (spring-boot-starter-mail).
 *
 * JavaMail es bloqueante → se envuelve en Mono.fromCallable + Schedulers.boundedElastic()
 * para no bloquear el event loop de Netty/WebFlux.
 *
 * Configuración requerida en application.properties:
 *   spring.mail.host, spring.mail.port, spring.mail.username, spring.mail.password
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationAdapter implements NotificationSenderPort {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Override
    public Mono<String> send(Notification notification) {
        return Mono.fromCallable(() -> {
            log.info("[EMAIL] Intentando enviar | plantilla={} to={} from={}",
                    notification.template(), notification.recipient(), fromAddress);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromAddress, "Motoya Digital");
            helper.setTo(notification.recipient());
            helper.setSubject(notification.template().getEmailSubject());
            helper.setText(notification.renderedContent(), true);

            log.info("[EMAIL] Conectando al servidor SMTP y enviando...");
            mailSender.send(mimeMessage);

            String messageId = mimeMessage.getMessageID() != null ? mimeMessage.getMessageID() : "sin-id";
            log.info("[EMAIL] ✓ Enviado | plantilla={} to={} messageId={}",
                    notification.template(), notification.recipient(), messageId);
            return messageId;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(ex -> log.error("[EMAIL] ✗ Falló | to={} plantilla={} error={}",
                notification.recipient(), notification.template(), ex.getMessage(), ex));
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }
}
