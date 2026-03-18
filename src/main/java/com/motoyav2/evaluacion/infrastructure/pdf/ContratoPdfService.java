package com.motoyav2.evaluacion.infrastructure.pdf;

import com.motoyav2.evaluacion.domain.model.Expediente;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContratoPdfService {

    private final SpringTemplateEngine templateEngine;

    public byte[] generar(Expediente expediente, Map<String, Object> camposAdicionales) {
        try {
            Context ctx = new Context();
            ctx.setVariable("solicitud",  expediente.getSolicitud());
            ctx.setVariable("titular",    expediente.getTitular());
            ctx.setVariable("fiador",     expediente.getFiador());
            ctx.setVariable("vehiculo",   expediente.getVehiculo());
            ctx.setVariable("asesor",     expediente.getAsesorAsignado());
            if (camposAdicionales != null) {
                camposAdicionales.forEach(ctx::setVariable);
            }

            String html = templateEngine.process("evaluacion/contrato-financiamiento", ctx);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generando contrato PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Error generando contrato PDF", e);
        }
    }
}
