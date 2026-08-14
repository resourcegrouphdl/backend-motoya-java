package com.motoyav2.notifications.infrastructure.config;

import com.motoyav2.notifications.infrastructure.channel.whatsapp.MetaWhatsAppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Configuración del módulo de notificaciones.
 *
 * Define un TemplateEngine propio (notificationTemplateEngine) para no interferir
 * con el TemplateEngine autoconfigurado por Spring Boot usado en la generación de PDFs.
 *
 * Resolvedores configurados:
 *   1. TEXT resolver → notification-templates/whatsapp/*.txt  (referencia/fallback)
 *   2. HTML resolver → notification-templates/email/*.html
 */
// @EnableScheduling deshabilitado 2026-08-13: se desactivan todos los @Scheduled del
// backend (cobranza, finanzas, evaluación, contabilidad, notifications, alertascenter).
// Sin esta anotación, Spring no arranca el ScheduledAnnotationBeanPostProcessor y
// ningún @Scheduled del proyecto se ejecuta, sin tener que tocar cada scheduler.
@Configuration
public class NotificationConfig {

    /** WebClient para Meta WhatsApp Cloud API (graph.facebook.com). */
    @Bean("metaWhatsAppWebClient")
    public WebClient metaWhatsAppWebClient(MetaWhatsAppProperties properties) {
        return WebClient.builder()
                .baseUrl("https://graph.facebook.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean("notificationTemplateEngine")
    public TemplateEngine notificationTemplateEngine() {
        TemplateEngine engine = new TemplateEngine();
        engine.addTemplateResolver(textTemplateResolver());
        engine.addTemplateResolver(htmlTemplateResolver());
        return engine;
    }

    /**
     * Resolver TEXT: para WhatsApp.
     * Busca en classpath:/notification-templates/{path}.txt
     * Usa sintaxis Thymeleaf TEXT: [(${variable})]
     */
    private ClassLoaderTemplateResolver textTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("notification-templates/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);
        resolver.setOrder(1);
        resolver.setCacheable(true);
        return resolver;
    }

    /**
     * Resolver HTML: para Email.
     * Busca en classpath:/notification-templates/{path}.html
     * Usa sintaxis Thymeleaf HTML: [[${variable}]] o th:text
     */
    private ClassLoaderTemplateResolver htmlTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("notification-templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);
        resolver.setOrder(2);
        resolver.setCacheable(true);
        return resolver;
    }
}
