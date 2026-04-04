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
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromAddress, "Motoya");
            helper.setTo(notification.recipient());
            helper.setSubject(notification.template().getEmailSubject());
            helper.setText(notification.renderedContent(), true); // true = isHtml

            mailSender.send(mimeMessage);

            // Capturar messageId del servidor SMTP para trazabilidad
            String messageId = mimeMessage.getMessageID() != null ? mimeMessage.getMessageID() : "";
            log.info("[EMAIL] ✓ Enviado | plantilla={} to={} messageId={}",
                    notification.template(), notification.recipient(), messageId);
            return messageId;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(ex -> {
            log.error("[EMAIL] ✗ Error enviando a={} error={}", notification.recipient(), ex.getMessage());
            return ex;
        });
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }
}
