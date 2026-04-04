package com.motoyav2.finanzas.infrastructure.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Renderiza templates Thymeleaf (XML mode) a bytes PDF usando OpenHTMLtoPDF.
 * Reutiliza el mismo patrón que ThymeleafPdfRenderer del módulo contrato.
 */
@Component
public class FinanzasPdfRenderer {

    private final TemplateEngine templateEngine;

    public FinanzasPdfRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    public byte[] render(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        String xhtml = templateEngine.process(templateName, context);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF [" + templateName + "]: " + e.getMessage(), e);
        }
    }
}
