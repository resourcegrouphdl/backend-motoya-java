package com.motoyav2.notifications.infrastructure.channel.whatsapp;

import com.motoyav2.notifications.domain.model.Notification;
import com.motoyav2.notifications.domain.model.NotificationChannel;
import com.motoyav2.notifications.domain.ports.out.NotificationSenderPort;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Adaptador de salida: envío de mensajes WhatsApp vía Twilio SDK.
 *
 * Usa el circuit breaker "twilio" (ya configurado en application.properties).
 * La inicialización de Twilio (Twilio.init) se hace en el constructor para
 * reutilizar la sesión de auth en todas las llamadas.
 *
 * Número origen: twilio.whatsapp-from (p.ej. "whatsapp:+14155238886" en sandbox)
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "notifications.whatsapp.provider",
        havingValue = "twilio")
public class TwilioWhatsAppNotificationAdapter implements NotificationSenderPort {

    private final String fromNumber;

    public TwilioWhatsAppNotificationAdapter(
            @Value("${twilio.account-sid}") String accountSid,
            @Value("${twilio.auth-token}") String authToken,
            @Value("${twilio.whatsapp-from}") String fromNumber) {
        Twilio.init(accountSid, authToken);
        // El from ya viene con el prefijo "whatsapp:" desde application.properties
        this.fromNumber = fromNumber;
    }

    @Override
    @CircuitBreaker(name = "twilio", fallbackMethod = "fallback")
    public Mono<Void> send(Notification notification) {
        return Mono.fromCallable(() -> {
            String to = "whatsapp:+" + sanitizePhone(notification.recipient());

            Message message = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(fromNumber),
                    notification.renderedContent()
            ).create();

            log.info("[WHATSAPP] ✓ Enviado | sid={} to={}", message.getSid(), notification.recipient());
            return message;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }

    public Mono<Void> fallback(Notification notification, Throwable ex) {
        log.error("[WHATSAPP] Circuit breaker abierto | destinatario={} error={}",
                notification.recipient(), ex.getMessage());
        return Mono.error(ex);
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    /**
     * Elimina caracteres no numéricos excepto el '+' inicial.
     * Si el número ya tiene código de país (51...), lo respeta.
     */
    private String sanitizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        // Si es un número peruano de 9 dígitos sin código de país, agrega 51
        if (digits.length() == 9) {
            return "51" + digits;
        }
        return digits;
    }
}
