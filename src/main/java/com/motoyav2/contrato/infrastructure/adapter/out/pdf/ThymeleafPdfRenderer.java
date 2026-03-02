package com.motoyav2.contrato.infrastructure.adapter.out.pdf;

import com.motoyav2.contrato.domain.enums.TipoDocumentoGenerado;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Renderiza templates Thymeleaf a PDF usando OpenHTMLtoPDF (PDFBox).
 *
 * OpenHTMLtoPDF es un fork moderno de Flying Saucer con soporte CSS3 real,
 * box model correcto y sin bugs de layout en márgenes laterales.
 *
 * Thymeleaf sigue en modo XML para producir XHTML válido que OpenHTMLtoPDF
 * puede parsear directamente como string (sin pasar por DocumentBuilder).
 */
@Component
public class ThymeleafPdfRenderer {

    private static final Map<TipoDocumentoGenerado, String> TEMPLATE_MAP = Map.of(
            TipoDocumentoGenerado.CONTRATO,          "pdf/contrato-venta",
            TipoDocumentoGenerado.CRONOGRAMA,        "pdf/cronograma-pagos",
            TipoDocumentoGenerado.PAGARE,            "pdf/pagare",
            TipoDocumentoGenerado.CARTA_INSTRUCCION, "pdf/carta-instruccion"
    );

    private final TemplateEngine templateEngine;

    public ThymeleafPdfRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    public byte[] render(TipoDocumentoGenerado tipo, Map<String, Object> variables) {
        String templateName = TEMPLATE_MAP.get(tipo);
        if (templateName == null) {
            throw new IllegalArgumentException("No hay template para el tipo: " + tipo);
        }

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
            throw new RuntimeException("Error generando PDF para " + tipo + ": " + e.getMessage(), e);
        }
    }
}
