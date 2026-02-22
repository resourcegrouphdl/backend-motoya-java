package com.motoyav2.contrato.infrastructure.adapter.out.pdf;

import com.motoyav2.contrato.domain.enums.TipoDocumentoGenerado;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Renderiza templates Thymeleaf a PDF usando Flying Saucer (OpenPDF).
 *
 * IMPORTANTE: usa su propio TemplateEngine en modo XML (no el auto-configurado en modo HTML).
 * Flying Saucer requiere XHTML válido; el modo HTML de Thymeleaf produce HTML5
 * con void elements sin cerrar (<br>, <meta>) que el parser XML de Flying Saucer rechaza.
 */
@Component
public class ThymeleafPdfRenderer {

    private static final String XHTML_DOCTYPE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" " +
            "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n";

    private static final Map<TipoDocumentoGenerado, String> TEMPLATE_MAP = Map.of(
            TipoDocumentoGenerado.CONTRATO,         "pdf/contrato-venta",
            TipoDocumentoGenerado.CRONOGRAMA,       "pdf/cronograma-pagos",
            TipoDocumentoGenerado.PAGARE,           "pdf/pagare",
            TipoDocumentoGenerado.CARTA_INSTRUCCION,"pdf/carta-instruccion"
    );

    private final TemplateEngine templateEngine;

    public ThymeleafPdfRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML);   // XML mode: mantiene <br/>, <meta/> etc.
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
        String body = templateEngine.process(templateName, context);

        // Flying Saucer necesita DOCTYPE XHTML 1.0 para procesar CSS correctamente
        String xhtml = XHTML_DOCTYPE + body;

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            renderer.createPDF(os);
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF para " + tipo + ": " + e.getMessage(), e);
        }
    }
}
