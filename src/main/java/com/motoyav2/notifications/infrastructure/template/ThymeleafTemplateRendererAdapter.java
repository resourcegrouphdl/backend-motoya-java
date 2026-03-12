package com.motoyav2.notifications.infrastructure.template;

import com.motoyav2.notifications.domain.model.Notification;
import com.motoyav2.notifications.domain.model.NotificationChannel;
import com.motoyav2.notifications.domain.ports.out.TemplateRendererPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Locale;

/**
 * Adaptador de plantillas usando un TemplateEngine dedicado a notificaciones
 * (bean "notificationTemplateEngine", configurado en NotificationConfig).
 *
 * Resolución de rutas de plantillas:
 *   WHATSAPP → notification-templates/whatsapp/{templateName}  (.txt, modo TEXT)
 *   SMS      → notification-templates/sms/{templateName}       (.txt, modo TEXT)
 *   EMAIL    → notification-templates/email/{templateName}     (.html, modo HTML)
 *
 * Thymeleaf TEXT mode: usa [(${variable})] para interpolación.
 * Thymeleaf HTML mode: usa [[${variable}]] o th:text para interpolación.
 */
@Slf4j
@Component
public class ThymeleafTemplateRendererAdapter implements TemplateRendererPort {

    private final TemplateEngine notificationTemplateEngine;

    public ThymeleafTemplateRendererAdapter(
            @Qualifier("notificationTemplateEngine") TemplateEngine notificationTemplateEngine) {
        this.notificationTemplateEngine = notificationTemplateEngine;
    }

    @Override
    public Mono<String> render(Notification notification) {
        return Mono.fromCallable(() -> {
            Context ctx = new Context(Locale.forLanguageTag("es-PE"));
            if (notification.variables() != null) {
                notification.variables().forEach(ctx::setVariable);
            }

            String templatePath = resolveTemplatePath(notification);
            String result = notificationTemplateEngine.process(templatePath, ctx);

            log.debug("[TEMPLATE] Renderizado | plantilla={} canal={}",
                    notification.template(), notification.channel());
            return result;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Construye la ruta de plantilla según el canal.
     * El prefijo "notification-templates/" y la extensión son añadidos por el resolver.
     */
    private String resolveTemplatePath(Notification notification) {
        String channel = resolveChannelFolder(notification.channel());
        String templateName = notification.template().getTemplateName();
        return channel + "/" + templateName;
    }

    private String resolveChannelFolder(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> "email";
            case SMS -> "sms";
            default -> "whatsapp";
        };
    }
}
