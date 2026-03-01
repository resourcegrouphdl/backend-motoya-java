package com.motoyav2.contrato.infrastructure.adapter.out.pdf;

import com.motoyav2.contrato.domain.enums.TipoDocumentoGenerado;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.w3c.dom.Document;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.Map;

/**
 * Renderiza templates Thymeleaf a PDF usando Flying Saucer (OpenPDF).
 *
 * IMPORTANTE: usa su propio TemplateEngine en modo XML (no el auto-configurado en modo HTML).
 * Flying Saucer requiere XHTML válido; el modo HTML de Thymeleaf produce HTML5
 * con void elements sin cerrar (<br>, <meta>) que el parser XML de Flying Saucer rechaza.
 *
 * Se usa un DocumentBuilder con DTD externo deshabilitado para evitar que Flying Saucer
 * intente descargar http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd en cada render,
 * lo que causaba timeouts en Cloud Run (status 0 en el cliente).
 */
@Component
public class ThymeleafPdfRenderer {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private static final DocumentBuilderFactory DBF;

    static {
        DBF = DocumentBuilderFactory.newInstance();
        DBF.setValidating(false);
        DBF.setNamespaceAware(true);
        try {
            DBF.setFeature("http://xml.org/sax/features/validation", false);
            DBF.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            DBF.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DBF.setFeature("http://xml.org/sax/features/external-general-entities", false);
            DBF.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
            // Si el parser no soporta estos features, continúa sin ellos
        }
    }

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

        String xhtml = XML_HEADER + body;

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            DocumentBuilder db = DBF.newDocumentBuilder();
            db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            Document doc = db.parse(new InputSource(new StringReader(xhtml)));

            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocument(doc, null);
            renderer.layout();
            renderer.createPDF(os);
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF para " + tipo + ": " + e.getMessage(), e);
        }
    }
}
