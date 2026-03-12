package com.motoyav2.notifications.infrastructure.channel.sms;

import com.motoyav2.notifications.domain.model.Notification;
import com.motoyav2.notifications.domain.model.NotificationChannel;
import com.motoyav2.notifications.domain.ports.out.NotificationSenderPort;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Adaptador de salida: envío de SMS vía Twilio SDK.
 * Comparte las credenciales Twilio inicializadas por el adaptador de WhatsApp.
 *
 * Número SMS configurado en: notifications.sms.from-number
 * (diferente al número WhatsApp, Twilio los separa)
 */
@Slf4j
@Component
public class TwilioSmsNotificationAdapter implements NotificationSenderPort {

    private final String fromNumber;

    public TwilioSmsNotificationAdapter(
            @Value("${notifications.sms.from-number:+14155238886}") String fromNumber) {
        this.fromNumber = fromNumber;
    }

    @Override
    @CircuitBreaker(name = "twilio", fallbackMethod = "fallback")
    public Mono<Void> send(Notification notification) {
        return Mono.fromCallable(() -> {
            String to = "+" + sanitizePhone(notification.recipient());

            Message message = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(fromNumber),
                    notification.renderedContent()
            ).create();

            log.info("[SMS] ✓ Enviado | sid={} to={}", message.getSid(), notification.recipient());
            return message;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }

    public Mono<Void> fallback(Notification notification, Throwable ex) {
        log.error("[SMS] Circuit breaker abierto | destinatario={} error={}",
                notification.recipient(), ex.getMessage());
        return Mono.error(ex);
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    private String sanitizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 9) {
            return "51" + digits;
        }
        return digits;
    }
}
